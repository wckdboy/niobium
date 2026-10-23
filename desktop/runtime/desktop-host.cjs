'use strict';
// The only desktop TCP listener exposes the authenticated Niobium API.
// DSH itself listens on a mode-0700 private Unix socket, never a public port.
const fs = require('node:fs/promises');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const {spawn} = require('node:child_process');

async function prepareAdapters(root, home) {
  const directory = path.join(home, '.niobium-runtime');
  await fs.mkdir(directory, {recursive: true, mode: 0o700});
  if (!(await fs.lstat(directory)).isDirectory()) throw new Error('The private adapter directory needs repair.');
  await fs.chmod(directory, 0o700);
  async function atomicWrite(file, bytes) {
    const stage = file + '.' + crypto.randomUUID();
    try { await fs.writeFile(stage, bytes, {mode: 0o600, flag: 'wx'}); await fs.rename(stage, file); }
    finally { await fs.rm(stage, {force: true}); }
  }
  for (const name of ['niobium-agent.cjs', 'niobium-messaging.cjs']) {
    await atomicWrite(path.join(directory, name), await fs.readFile(path.join(root, name)));
  }
  const pluginPath = path.join(directory, 'niobium-agent.cjs');
  // Older previews stored a bundle/translocation path. Migrate only generated
  // Niobium presets, before DSH scans them. Keep unrelated presets untouched.
  let metadata;
  try { metadata = JSON.parse(await fs.readFile(path.join(home, 'niobium-pixels.json'), 'utf8')); }
  catch (error) {
    if (error.code === 'ENOENT') return pluginPath;
    if (error instanceof SyntaxError) throw new Error('Local Pixel metadata needs repair. Its original file is preserved.');
    throw error;
  }
  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) throw new Error('Local Pixel metadata needs repair. Its original file is preserved.');
  for (const [id, entry] of Object.entries(metadata)) {
    if (!/^[a-z][a-z0-9-]{1,30}$/.test(id) || (!Array.isArray(entry?.enabled_toolsets) || !entry.enabled_toolsets.includes('messaging')) || entry.ui_meta?.niobium?.pixel_spec?.name !== id) continue;
    const preset = path.join(home, '.agent-presets', id, 'agent.cordis.yml');
    let rows;
    try {
      for (const folder of [path.join(home, '.agent-presets'), path.dirname(preset)]) {
        if (!(await fs.lstat(folder)).isDirectory()) throw new Error('A local preset directory is linked or needs repair: ' + id);
      }
      if (!(await fs.lstat(preset)).isFile()) throw new Error('A local preset is linked or needs repair: ' + id);
      rows = JSON.parse(await fs.readFile(preset, 'utf8'));
    } catch (error) {
      if (error.code === 'ENOENT') continue;
      if (error instanceof SyntaxError) throw new Error('Review this local preset before updating its adapter: ' + id);
      throw error;
    }
    if (!Array.isArray(rows)) throw new Error('Review this local preset before updating its adapter: ' + id);
    let changed = false;
    for (const row of rows) {
      if (row.id !== 'niobium-messaging' || row.name === pluginPath) continue;
      if (typeof row.name !== 'string' || !path.isAbsolute(row.name) || path.basename(row.name) !== 'niobium-agent.cjs') throw new Error('Review this Pixel’s modified messaging plugin: ' + id);
      row.name = pluginPath; changed = true;
    }
    if (changed) await atomicWrite(preset, JSON.stringify(rows, null, 2) + '\n');
  }
  return pluginPath;
}

async function start({root, home, workspace, node = process.execPath, signal, onDiagnostic}) {
  if (![root, home, workspace].every(p => typeof p === 'string' && path.isAbsolute(p))) throw new Error('Absolute runtime, home and workspace paths are required.');
  if (signal?.aborted) throw new Error('Local startup cancelled.');
  await fs.mkdir(home, {recursive: true, mode: 0o700});
  await fs.chmod(home, 0o700);
  await fs.mkdir(workspace, {recursive: true, mode: 0o700});
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'nb-'));
  await fs.chmod(temp, 0o700);
  const socketPath = path.join(temp, 'dsh.sock');
  const key = crypto.randomBytes(32).toString('hex');
  const modules = path.join(root, 'node_modules');
  const ownedSockets = new Set();
  let stopped = false, child;
  let server;
  async function stop() {
    if (stopped) return;
    stopped = true;
    for (const socket of ownedSockets) socket.destroy();
    server?.close();
    if (child?.pid) {
      try { process.kill(-child.pid, 'SIGTERM'); } catch (e) { if (e.code !== 'ESRCH') throw e; }
      // DSH allows five seconds to dispose its managed detached process trees.
      if (child.exitCode === null && child.signalCode === null) await new Promise(resolve => {
        const done = () => { clearTimeout(timer); child.off('exit', done); resolve(); };
        const timer = setTimeout(done, 6000);
        child.once('exit', done);
      });
      try { process.kill(-child.pid, 'SIGKILL'); } catch (e) { if (e.code !== 'ESRCH') throw e; }
    }
    await fs.rm(temp, {recursive: true, force: true});
  }
  try {
    const pluginPath = await prepareAdapters(root, home);
    child = spawn(node, ['--require', path.join(root, 'niobium-local.cjs'), '--require', pluginPath,
      path.join(modules, '@deepseek-ai/dsh/lib/bin.js'), 'web'], {
      cwd: workspace, detached: true, stdio: ['ignore', 'pipe', 'pipe'],
      env: {...process.env, DSH_HOME: home, NIOBIUM_DSH_HOME: home, NIOBIUM_WORKSPACE: workspace,
        NIOBIUM_DSH_MODULES: modules, NIOBIUM_SOCKET: socketPath, NIOBIUM_AGENT_TOKEN: key, DSH_PORT: '3080',
        // Never preload the adapter in tools spawned by the agent.
        NODE_OPTIONS: ''}
    });
    let failure;
    child.on('error', error => { failure = error; });
    child.on('exit', code => { if (!stopped) failure = new Error('Local DSH stopped (' + code + ').'); });
    // Runtime output can include configuration. It stays in this process and
    // never becomes a user log, crash attachment, or a credential-bearing URL.
    let diagnostics = '';
    for (const stream of [child.stdout, child.stderr]) stream.on('data', value => {
      diagnostics = (diagnostics + value.toString()).slice(-32768);
      onDiagnostic?.(value.toString().replaceAll(key, '[private token]'));
    });
    const status = () => new Promise((resolve, reject) => {
      const request = http.get({socketPath, path: '/niobium/api/status', headers: {host: '127.0.0.1:3080', authorization: 'Bearer ' + key}}, response => {
        response.resume(); response.once('end', () => response.statusCode === 200 ? resolve() : reject(new Error('Local API is not ready.')));
      });
      request.setTimeout(1500, () => request.destroy(new Error('Local API startup timed out.')));
      request.on('error', reject);
    });
    const deadline = Date.now() + 45000;
    while (true) {
      if (signal?.aborted) throw new Error('Local startup cancelled.');
      if (failure) throw failure;
      try { await status(); break; } catch {}
      if (Date.now() >= deadline) throw new Error('DSH did not become ready. Check the bundled runtime and workspace permissions.');
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    let authority;
    function authorized(request) {
      if (request.headers.host !== authority || (request.headers.origin && request.headers.origin !== 'http://' + authority)) return false;
      let url;
      try { url = new URL(request.url, 'http://' + authority); } catch { return false; }
      if (!['/niobium/api/status', '/niobium/api/ws'].includes(url.pathname)) return false;
      const value = url.searchParams.get('token') || (request.headers.authorization || '').replace(/^Bearer /, '');
      const a = Buffer.from(value), b = Buffer.from(key);
      return a.length === b.length && crypto.timingSafeEqual(a, b);
    }
    server = http.createServer((request, response) => {
      if (!authorized(request) || request.method !== 'GET' || !request.url.startsWith('/niobium/api/status')) {
        response.writeHead(403); response.end(); return;
      }
      const upstream = http.get({socketPath, path: '/niobium/api/status', headers: {host: '127.0.0.1:3080', authorization: 'Bearer ' + key}}, reply => {
        response.writeHead(reply.statusCode, {'content-type': 'application/json', 'cache-control': 'no-store'}); reply.pipe(response);
      });
      upstream.setTimeout(5000, () => upstream.destroy());
      upstream.on('error', () => { if (!response.headersSent) response.writeHead(503); response.end(); });
      response.on('close', () => upstream.destroy());
    });
    server.maxConnections = 12;
    server.headersTimeout = 5000;
    server.requestTimeout = 5000;
    server.on('connection', socket => { ownedSockets.add(socket); socket.once('close', () => ownedSockets.delete(socket)); });
    server.on('upgrade', (request, socket, head) => {
      if (!authorized(request) || new URL(request.url, 'http://' + authority).pathname !== '/niobium/api/ws') { socket.destroy(); return; }
      const upstream = net.connect(socketPath);
      ownedSockets.add(upstream); upstream.once('close', () => ownedSockets.delete(upstream));
      upstream.on('connect', () => {
        const headers = ['GET ' + request.url + ' HTTP/1.1', 'Host: 127.0.0.1:3080', 'Connection: Upgrade', 'Upgrade: websocket'];
        for (const field of ['sec-websocket-key', 'sec-websocket-version', 'sec-websocket-protocol']) {
          if (request.headers[field]) headers.push(field + ': ' + request.headers[field]);
        }
        upstream.write(headers.join('\r\n') + '\r\n\r\n');
        if (head.length) upstream.write(head);
        socket.pipe(upstream).pipe(socket);
      });
      upstream.on('error', () => socket.destroy()); socket.on('error', () => upstream.destroy());
      socket.once('close', () => upstream.destroy()); upstream.once('close', () => socket.destroy());
    });
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    authority = '127.0.0.1:' + server.address().port;
    return {origin: 'http://' + authority + '/niobium', token: key, stop, child, socketPath};
  } catch (error) { await stop(); throw error; }
}

module.exports = {start, prepareAdapters};
if (require.main === module) {
  let runtime;
  let ending = false;
  const controller = new AbortController();
  let launched;
  const end = async () => { if (ending) return; ending = true; controller.abort(); await launched?.catch(() => {}); await runtime?.stop(); process.exit(0); };
  process.once('SIGTERM', end); process.once('SIGINT', end);
  process.stdin.resume(); process.stdin.once('end', end);
  launched = start({root: __dirname, home: process.env.NIOBIUM_DSH_HOME, workspace: process.env.NIOBIUM_WORKSPACE, signal: controller.signal});
  launched.then(async value => {
    runtime = value;
    if (ending) { await runtime.stop(); return; }
    process.stdout.write(JSON.stringify({type: 'ready', origin: value.origin, token: value.token}) + '\n');
    value.child.once('exit', () => { if (!ending) { process.stderr.write('Local DSH stopped. Start it again under Engines.\n'); end(); } });
  }).catch(error => { process.stderr.write(error.message + '\n'); process.exitCode = 1; process.stdin.destroy(); });
}
