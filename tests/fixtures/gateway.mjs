import { createServer } from 'node:http';
import { createHash, randomUUID } from 'node:crypto';
const token = 'fixture-token+&?';
let androidMode = null;
const androidHistory = [], androidRequests = [];
const androidImages = new Map();
let sessionReplies = [];
const sessionRequests = [];
const server = createServer((request, response) => {
  const valid = request.url === '/proxy/api/status' && request.headers['x-hermes-session-token'] === token && request.headers.authorization === 'Bearer ' + token;
  response.writeHead(valid ? 200 : 401, { 'Content-Type': 'application/json' });
  response.end(valid ? '{"version":"fixture"}' : '{}');
});
server.on('upgrade', (request, socket) => {
  let pingRequests = 0;
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
  const calls = [], profiles = new Map();
  let failMethod = '';
  const handle = frame => {
    const result = value => send({ jsonrpc: '2.0', id: frame.id, result: value });
    const reject = message => send({ jsonrpc: '2.0', id: frame.id, error: { code: 4409, message } });
    if (!frame.method) { event('approval.answer', frame.result); return; }
    if (!frame.method.startsWith('fixture.')) calls.push({ method: frame.method, params: frame.params });
    if (frame.method === failMethod) { reject('Fixture setup failure'); return; }
    switch (frame.method) {
      case 'client.capabilities': result({declines_not_shown:false}); break;
      case 'runtime.describe': result({session_create:{sources:['gui']}}); break;
      case 'gateway.capabilities': result({}); break;
      case 'groups.capabilities': result({driver:false,methods:['groups.list']}); break;
      case 'groups.list': result({rooms:[]}); break;
      case 'groups.log': result({events:[]}); break;
      case 'profiles.list': result({profiles:[{name:'remote-fixture',display_name:'Remote fixture',canonical_session:{id:'fixture-session'}}]}); break;
      case 'tools.list': result({toolsets:[]}); break;
      case 'model.options': result({providers:[]}); break;
      case 'fixture.sessions':
        if (Array.isArray(frame.params?.replies)) {sessionReplies=frame.params.replies;sessionRequests.length=0;}
        result({requests:sessionRequests});break;
      case 'session.list': {
        sessionRequests.push({profile:frame.params.profile,pings:pingRequests});
        const reply=sessionReplies.shift()||{sessions:[]};
        setTimeout(()=>reply.fail?reject('Session list unavailable'):result({sessions:reply.sessions||[]}),reply.delay||0);break;
      }
      case 'session.resume':
        if (Object.keys(frame.params || {}).some(key=>!['session_id','profile','source','omit_messages'].includes(key))) { reject('Unexpected session.resume keys'); break; }
        result({session_id:androidMode ? frame.params.session_id : 'fixture-session',messages:androidMode ? androidHistory : [],revision:0}); break;
      case 'fixture.android':
        if (typeof frame.params?.mode === 'string') androidMode = {mode:frame.params.mode,delay:frame.params.delay || 0,attachDelay:frame.params.attach_delay || 0,pathless:frame.params.pathless===true};
        result({requests:androidRequests,history:androidHistory,queued_images:androidImages.size,pings:pingRequests}); break;
      case 'image.attach_bytes': {
        const path = 'fixture-image-' + randomUUID();androidImages.set(frame.params.session_id,path);
        androidRequests.push({method:frame.method,profile:frame.params.profile,session:frame.params.session_id});
        setTimeout(()=>result({attached:true,...(androidMode?.pathless?{}:{path}),count:1}),androidMode?.attachDelay || 0); break;
      }
      case 'image.detach': {
        const detached=androidImages.get(frame.params.session_id)===frame.params.path;
        if(detached)androidImages.delete(frame.params.session_id);
        androidRequests.push({method:frame.method,profile:frame.params.profile,session:frame.params.session_id});
        result({detached,count:androidImages.has(frame.params.session_id)?1:0});break;
      }
      case 'prompt.submit':
        if (androidMode) {
          androidRequests.push({method:frame.method,text:frame.params.text,submission_id:frame.params.submission_id,profile:frame.params.profile,session:frame.params.session_id,image:androidImages.get(frame.params.session_id)||null});
          if (androidMode.mode === 'reject') { result({accepted:false}); break; }
          androidImages.delete(frame.params.session_id);
          androidHistory.push({row_id:androidHistory.length+1,role:'user',text:frame.params.text});
          if (androidMode.mode === 'drop') { socket.destroy(); break; }
          setTimeout(()=>{ event('message.complete',{text:'Android confirmed reply'});result({accepted:true}); },androidMode.delay);
          break;
        }
        if (frame.params.text === 'fixture tool results') {
          for (const [id,payload] of [['local-failed',{is_error:true}],['remote-failed',{success:false}],['completed',{is_error:false}]]) {
            event('tool.start',{tool_id:id,name:'fixture'});
            event('tool.complete',{tool_id:id,result:'tool result',...payload});
          }
        }
        event('message.complete',{text:'Fixture reply'});
        result({accepted:true}); break;
      case 'fixture.configure': failMethod = frame.params.fail_method || ''; result({ ok: true }); break;
      case 'fixture.state': result({ calls, profiles: Array.from(profiles.values()) }); break;
      case 'profiles.create':
        if (profiles.has(frame.params.name)) { reject('Profile already exists'); break; }
        profiles.set(frame.params.name, { name: frame.params.name }); result({ ok: true }); break;
      case 'profiles.describe': result({ skills: [{ name: 'audit' }, { name: 'extra' }] }); break;
      case 'profiles.configure':
        if (!profiles.has(frame.params.name)) { reject('Profile does not exist'); break; }
        Object.assign(profiles.get(frame.params.name), frame.params); result({ ok: true }); break;
      case 'config.set': result({ ok: true }); break;
      case 'echo': result(frame.params); break;
      case 'delayed': setTimeout(() => result({ label: frame.params.label }), frame.params.delay); break;
      case 'fail': send({ jsonrpc: '2.0', id: frame.id, error: { code: 4409, message: 'Fixture rejection' } }); break;
      case 'wait': break;
      case 'drop': socket.destroy(); break;
      case 'request_approval':
        send({ jsonrpc: '2.0', id: 'approval-7', method: 'approval', params: { session_id: 'fixture-session', request_id: 'queue-9', command: 'fixture', choices: ['once','deny'] } });
        result({ requested: true }); break;
      default: reject('Unsupported fixture method: ' + frame.method); break;
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
      if (opcode === 9) {
        if (payload.length > 125) { socket.destroy(); return; }
        pingRequests++;
        socket.write(Buffer.concat([Buffer.from([0x8a,payload.length]),payload]));
        continue;
      }
      if (opcode === 1) handle(JSON.parse(payload.toString()));
    }
  });
  event('gateway.ready', {});
});
server.listen(0, '127.0.0.1', () => console.log('PORT ' + server.address().port));
