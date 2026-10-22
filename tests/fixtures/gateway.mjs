import { createServer } from 'node:http';
import { createHash } from 'node:crypto';
const token = 'fixture-token+&?';
const server = createServer((request, response) => {
  const valid = request.url === '/proxy/api/status' && request.headers['x-hermes-session-token'] === token && request.headers.authorization === 'Bearer ' + token;
  response.writeHead(valid ? 200 : 401, { 'Content-Type': 'application/json' });
  response.end(valid ? '{"version":"fixture"}' : '{}');
});
server.on('upgrade', (request, socket) => {
  const url = new URL(request.url, 'http://localhost');
  if (url.pathname !== '/proxy/api/ws' || url.searchParams.get('token') !== token) { socket.destroy(); return; }
  const accept = createHash('sha1').update(request.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + '\r\n\r\n');
  const send = value => {
    if (socket.destroyed) return;
    const data = Buffer.from(JSON.stringify(value));
    const header = data.length < 126 ? Buffer.from([0x81, data.length]) : Buffer.from([0x81,126,data.length >> 8,data.length & 255]);
    socket.write(Buffer.concat([header, data]));
  };
  const event = (type, payload) => send({ jsonrpc: '2.0', method: 'event', params: { type, session_id: 'fixture-session', payload } });
  const handle = frame => {
    const result = value => send({ jsonrpc: '2.0', id: frame.id, result: value });
    if (!frame.method) { event('approval.answer', frame.result); return; }
    switch (frame.method) {
      case 'echo': result(frame.params); break;
      case 'delayed': setTimeout(() => result({ label: frame.params.label }), frame.params.delay); break;
      case 'fail': send({ jsonrpc: '2.0', id: frame.id, error: { code: 4409, message: 'Fixture rejection' } }); break;
      case 'wait': break;
      case 'drop': socket.destroy(); break;
      case 'request_approval':
        send({ jsonrpc: '2.0', id: 'approval-7', method: 'approval', params: { session_id: 'fixture-session', request_id: 'queue-9', command: 'fixture', choices: ['once','deny'] } });
        result({ requested: true }); break;
      default: result({}); break;
    }
  };
  let buffered = Buffer.alloc(0);
  socket.on('error', () => {});
  socket.on('data', chunk => {
    buffered = Buffer.concat([buffered, chunk]);
    while (buffered.length >= 2) {
      const opcode = buffered[0] & 15, masked = Boolean(buffered[1] & 128);
      let size = buffered[1] & 127, offset = 2;
      if (size === 127) { socket.destroy(); return; }
      if (size === 126) { if (buffered.length < 4) return; size = buffered.readUInt16BE(2); offset = 4; }
      if (buffered.length < offset + (masked ? 4 : 0) + size) return;
      const mask = masked ? buffered.subarray(offset, offset + 4) : null;
      if (masked) offset += 4;
      const payload = Buffer.from(buffered.subarray(offset, offset + size));
      if (mask) for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
      buffered = buffered.subarray(offset + size);
      if (opcode === 8) { socket.end(Buffer.from([0x88,0])); return; }
      if (opcode === 1) handle(JSON.parse(payload.toString()));
    }
  });
  event('gateway.ready', {});
});
server.listen(0, '127.0.0.1', () => console.log('PORT ' + server.address().port));
