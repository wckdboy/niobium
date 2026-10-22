import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {createRequire} from 'node:module';

const require = createRequire(new URL('../runtime/package.json', import.meta.url));
const {WebSocket, WebSocketServer} = require('ws');
const {connectEventStream} = require('./niobium-agent.cjs');
const deferred = () => Promise.withResolvers();

async function fixture(t, upgrade) {
  let attempts = 0;
  const server = http.createServer((_request, response) => response.end());
  const sockets = new Set();
  server.on('connection', socket => {
    sockets.add(socket);
    socket.once('close', () => sockets.delete(socket));
  });
  const wss = new WebSocketServer({noServer: true});
  server.on('upgrade', (request, socket, head) => {
    upgrade({attempt: ++attempts, socket,
      accept: () => wss.handleUpgrade(request, socket, head, peer => wss.emit('connection', peer))});
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(async () => {
    for (const peer of wss.clients) peer.terminate();
    for (const socket of sockets) socket.destroy();
    await new Promise(resolve => server.close(resolve));
    wss.close();
  });
  return {url: 'ws://127.0.0.1:' + server.address().port + '/api/events.mux', wss,
    attempts: () => attempts};
}

function observe(t, endpoint, options = {}) {
  const outcome = deferred(), failure = deferred(), waiting = deferred(), message = deferred();
  const events = {opened: 0, failed: 0, waiting: 0};
  const stop = connectEventStream({
    connect: () => new WebSocket(endpoint.url), timeoutMs: 1500, retryDelayMs: 10,
    onOpen: () => { events.opened++; outcome.resolve({type: 'open'}); },
    onMessage: raw => message.resolve(raw.toString()),
    onFailure: reason => { events.failed++; failure.resolve(reason); outcome.resolve({type: 'failure', reason}); },
    onWaiting: reason => { events.waiting++; waiting.resolve(reason); },
    ...options
  });
  t.after(stop);
  return {stop, events, outcome: outcome.promise, failure: failure.promise, waiting: waiting.promise,
    message: message.promise};
}

test('the event stream survives DSH accepting HTTP before its upgrade route exists', {timeout: 5000}, async t => {
  const endpoint = await fixture(t, ({attempt, socket, accept}) => attempt < 4 ? socket.destroy() : accept());
  endpoint.wss.on('connection', peer => peer.send(JSON.stringify({payload: {type: 'session/event'}})));
  const stream = observe(t, endpoint);
  assert.deepEqual(await stream.outcome, {type: 'open'});
  assert.deepEqual(JSON.parse(await stream.message), {payload: {type: 'session/event'}});
  assert.equal(endpoint.attempts(), 4);
  assert.deepEqual(stream.events, {opened: 1, failed: 0, waiting: 1});
  stream.stop();
});

test('temporary 404 and 503 upgrade responses wait for the real route', {timeout: 5000}, async t => {
  const endpoint = await fixture(t, ({attempt, socket, accept}) => {
    if (attempt < 3) socket.end('HTTP/1.1 ' + (attempt === 1 ? '404 Not Found' : '503 Unavailable') + '\r\nContent-Length: 0\r\nConnection: close\r\n\r\n');
    else accept();
  });
  const stream = observe(t, endpoint);
  assert.deepEqual(await stream.outcome, {type: 'open'});
  assert.equal(endpoint.attempts(), 3);
  stream.stop();
});

for (const code of [401, 403]) test('HTTP ' + code + ' fails immediately without retrying authorization', {timeout: 5000}, async t => {
  const endpoint = await fixture(t, ({socket}) => socket.end('HTTP/1.1 ' + code + ' Refused\r\nContent-Length: 0\r\nConnection: close\r\n\r\n'));
  const stream = observe(t, endpoint);
  assert.deepEqual(await stream.outcome, {type: 'failure', reason: 'DSH event stream HTTP ' + code});
  await delay(70);
  assert.equal(endpoint.attempts(), 1);
  assert.deepEqual(stream.events, {opened: 0, failed: 1, waiting: 0});
});

test('an unavailable startup has one bounded deadline and stops all retries', {timeout: 5000}, async t => {
  const endpoint = await fixture(t, ({socket}) => socket.destroy());
  const started = performance.now();
  const stream = observe(t, endpoint, {timeoutMs: 180});
  const outcome = await stream.outcome;
  assert.equal(outcome.type, 'failure');
  assert.match(outcome.reason, /^DSH event stream unavailable \(ECONNRESET\); startup timed out$/);
  assert(performance.now() - started >= 150);
  assert(performance.now() - started < 1500);
  const attempts = endpoint.attempts();
  assert(attempts > 1 && attempts < 10);
  await delay(200);
  assert.equal(endpoint.attempts(), attempts);
  assert.deepEqual(stream.events, {opened: 0, failed: 1, waiting: 1});
});

test('closing the native client while waiting cancels retries and the deadline', {timeout: 5000}, async t => {
  const endpoint = await fixture(t, ({socket}) => socket.destroy());
  const stream = observe(t, endpoint, {timeoutMs: 150, retryDelayMs: 60});
  await stream.waiting;
  stream.stop();
  await delay(250);
  assert.equal(endpoint.attempts(), 1);
  assert.deepEqual(stream.events, {opened: 0, failed: 0, waiting: 1});
});

test('cancelling an unfinished upgrade leaves no error callback or later retry', {timeout: 5000}, async t => {
  const received = deferred();
  const endpoint = await fixture(t, ({socket}) => { received.resolve(); socket.on('error', () => {}); });
  const stream = observe(t, endpoint, {timeoutMs: 150});
  await received.promise;
  stream.stop();
  await delay(250);
  assert.equal(endpoint.attempts(), 1);
  assert.deepEqual(stream.events, {opened: 0, failed: 0, waiting: 0});
});

test('an established stream closing fails visibly and never reconnects or replays', {timeout: 5000}, async t => {
  const endpoint = await fixture(t, ({accept}) => accept());
  const connected = once(endpoint.wss, 'connection');
  const stream = observe(t, endpoint);
  assert.deepEqual(await stream.outcome, {type: 'open'});
  const [peer] = await connected;
  peer.close(1011, 'private-peer-reason-and-token');
  assert.equal(await stream.failure, 'DSH event stream closed');
  await delay(150);
  assert.equal(endpoint.attempts(), 1);
  assert.deepEqual(stream.events, {opened: 1, failed: 1, waiting: 0});
});

test('an invalid upgrade reports only its status, without server text or headers', {timeout: 5000}, async t => {
  const secret = 'private-server-token';
  const endpoint = await fixture(t, ({socket}) => socket.end('HTTP/1.1 200 ' + secret + '\r\nX-Private: ' + secret + '\r\nContent-Length: 0\r\nConnection: close\r\n\r\n'));
  const stream = observe(t, endpoint);
  assert.deepEqual(await stream.outcome, {type: 'failure', reason: 'DSH event stream HTTP 200'});
  assert.equal(endpoint.attempts(), 1);
});
