import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
const require = createRequire(import.meta.url);
const {createMessaging} = require('../runtime/niobium-messaging.cjs');
const {streamFrames,messagingTool,composition} = require('../runtime/niobium-agent.cjs');
async function until(predicate) { for (let i=0;i<400;i++) { if (await predicate()) return; await new Promise(r=>setTimeout(r,5)); } assert.fail('Fixture did not settle'); }
async function fixture() {
  const home = await mkdtemp(path.join(tmpdir(),'niobium-rooms-'));
  const sessions = [], prompts = [], cancelled = [], histories = new Map(), pending = new Map();
  let response = () => 'A real fixture reply.', accept = true, complete = true, grants = true;
  const presets = [{id:'standard',name:'Scout'},{id:'forge',name:'Forge',trust:'user'},{id:'constructor',name:'Constructor',trust:'user'}];
  const rpc = async (method,p) => {
    if (method === 'agentPreset.list') return {presets};
    if (method === 'session.list') return {items:sessions};
    if (method === 'session.create') { const sessionId='session-'+sessions.length; sessions.push({sessionId,agentPreset:p.agentPreset}); histories.set(sessionId,[]); return {sessionId}; }
    if (method === 'session.rename') return {};
    if (method === 'session.prompt') {
      prompts.push(p); const rows=histories.get(p.sessionId), base=rows.length;
      const add = (type,data) => rows.push({event:{seq:rows.length,time:Date.now(),type,data}});
      add('user/message',{content:p.content}); add('turn/start',{});
      if (complete) { add('assistant/message',{message:{content:[{type:'text',text:response(p)}]}}); add('turn/end',{reason:{kind:'completed'}}); }
      if (!accept) throw new Error('Simulated receipt loss');
      return {accepted:true};
    }
    if (method === 'session.history') return {events:histories.get(p.sessionId)||[],hasMore:false};
    if (method === 'workspace.archiveSession') { const index=sessions.findIndex(s=>s.sessionId===p.sessionId); if(index>=0)sessions.splice(index,1); return {}; }
    if (method === 'session.cancel') { cancelled.push(p.sessionId); return {}; }
    throw new Error('Unexpected fixture method '+method);
  };
  const make=()=>createMessaging({rpc,home,workspace:'/safe/workspace',metadata:async()=>grants ? {default:{enabled_toolsets:['messaging']},forge:{enabled_toolsets:['messaging']}} : {},pending,respond:async()=>({ok:true}),frames:streamFrames,pollMs:2,turnTimeoutMs:3000});
  let driver=make();
  const create=async(id='room-one')=>driver.request('groups.create',{room_id:id,name:'Workshop',members:[{profile:'default'},{profile:'forge'}]});
  const send=async(text='Discuss this',id='event-one')=>driver.request('groups.send',{room_id:'room-one',event_id:id,payload:{text}});
  const state=async()=>JSON.parse(await readFile(path.join(home,'niobium-conversations.json'),'utf8'));
  const idle=()=>until(async()=>Object.values((await state()).rooms).every(r=>r.deliveries.every(j=>['completed','failed','cancelled','uncertain'].includes(j.phase))));
  return {home,sessions,prompts,cancelled,histories,pending,rpc,create,send,state,idle,
    get driver(){return driver;},set response(fn){response=fn;},set accept(v){accept=v;},set complete(v){complete=v;},set grants(v){grants=v;},
    async restart(){await driver.close();driver=make();},async close(){await driver.close();await rm(home,{recursive:true,force:true});}};
}

test('local rooms validate membership and use distinct real provider sessions',async()=>{
  const f=await fixture();try{
    await assert.rejects(f.driver.request('groups.create',{room_id:'../escape',name:'x',members:[]}),/two to six/);
    await assert.rejects(f.driver.request('groups.create',{room_id:'valid',name:'x',members:[{profile:'default'},{profile:'missing'}]}),/distinct Pixels/);
    await f.create(); await f.send(); await f.idle();
    assert.equal(f.prompts.length,2);assert.notEqual(f.prompts[0].sessionId,f.prompts[1].sessionId);
    const log=await f.driver.request('groups.log',{room_id:'room-one'});
    assert.deepEqual(log.events.filter(e=>e.kind==='message.member').map(e=>e.actor.id),['default','forge']);
    assert(log.events.filter(e=>e.kind==='message.member').every(e=>e.payload.text==='A real fixture reply.'));
    assert.deepEqual([...await f.driver.ownSessionIDs()].sort(),f.sessions.map(s=>s.sessionId).sort());
  }finally{await f.close();}
});

test('duplicate send receipts remain idempotent after restart and transcript trimming',async()=>{
 const f=await fixture();try{
  await f.create();await Promise.all(Array.from({length:6},()=>f.send('@forge Check once')));await f.idle();
  assert.equal(f.prompts.length,1);
  await f.driver.close();const state=await f.state();state.rooms['room-one'].events=[];
  await writeFile(path.join(f.home,'niobium-conversations.json'),JSON.stringify(state));await f.restart();
  const replay=await f.send('@forge Check once');assert.equal(replay.accepted,true);assert.equal(f.prompts.length,1);
  await assert.rejects(f.send('Different content'),/different content/);
 }finally{await f.close();}
});

test('ambiguous prompt acceptance never automatically submits it again',async()=>{
 const f=await fixture();try{
  f.accept=false;await f.create();await f.send('@forge Work');await f.idle();
  assert.equal(f.prompts.length,1);assert.equal((await f.state()).rooms['room-one'].deliveries[0].phase,'uncertain');
  await f.restart();await f.driver.request('groups.list');await new Promise(r=>setTimeout(r,20));assert.equal(f.prompts.length,1);
 }finally{await f.close();}
});

test('accepted jobs recover by reading their existing session without paid replay',async()=>{
 const f=await fixture();try{
  f.complete=false;await f.create();await f.send('@forge Work');
  await until(async()=> (await f.state()).rooms['room-one'].deliveries[0].phase==='accepted');
  await f.restart();const rows=f.histories.get(f.prompts[0].sessionId);
  rows.push({event:{seq:2,time:Date.now(),type:'assistant/message',data:{message:{content:[{type:'text',text:'Recovered actual reply'}]}}}},
    {event:{seq:3,time:Date.now(),type:'turn/end',data:{reason:{kind:'completed'}}}});
  await f.driver.request('groups.list');await f.idle();assert.equal(f.prompts.length,1);
  assert((await f.driver.request('groups.log',{room_id:'room-one'})).events.some(e=>e.payload.text==='Recovered actual reply'));
 }finally{await f.close();}
});

test('bot feedback is attributed and bounded across successive mentions',async()=>{
 const f=await fixture();try{
  f.response=p=>p.sessionId==='session-0' ? '@forge Your turn.' : '@default Your turn.';
  await f.create();await f.send();await f.idle();
  const state=await f.state(), room=state.rooms['room-one'];
  assert.equal(f.prompts.length,4);assert(room.events.some(e=>e.kind==='turn.limited'));
  assert(room.events.filter(e=>e.kind==='message.member').every(e=>['default','forge'].includes(e.actor.id)));
 }finally{await f.close();}
});

test('message_agent derives the sender from its session and enforces reviewed access',async()=>{
 const f=await fixture();try{
  const made=await f.rpc('session.create',{agentPreset:'standard'});await f.driver.rememberUserPrompt(made.sessionId);
  f.grants=false;await assert.rejects(f.driver.messageFrom(made.sessionId,{recipient:'forge',text:'hello'}),/reviewed access/);
  f.grants=true;
  const directory = await f.driver.directoryFrom(made.sessionId);
  assert.equal(directory.pixels.find(p=>p.id==='default').handle,'scout');
  assert.deepEqual(directory.rooms,[]);
  const stop=new AbortController();stop.abort();await assert.rejects(f.driver.messageFrom(made.sessionId,{recipient:'forge',text:'hello'},stop.signal));
  const receipt=await f.driver.messageFrom(made.sessionId,{recipient:'forge',text:'hello',sender:'forged'});assert.equal(receipt.status,'queued');await f.idle();
  const log=await f.driver.request('groups.log',{room_id:receipt.room_id});
  assert.equal(log.events.find(e=>e.payload.text==='hello').actor.id,'default');assert.equal(f.prompts.length,1);
  const duplicate=await f.driver.messageFrom(made.sessionId,{recipient:'forge',text:'hello'});assert.equal(duplicate.status,'already_recorded');assert.equal(f.prompts.length,1);
 }finally{await f.close();}
});

test('stopping a room cancels accepted sessions and discards its queued followups',async()=>{
 const f=await fixture();try{
  f.complete=false;await f.create();await f.send();await until(async()=> (await f.state()).rooms['room-one'].deliveries[0].phase==='accepted');
  await f.driver.request('groups.stop',{room_id:'room-one'});await f.idle();assert.equal(f.prompts.length,1);assert.equal(f.cancelled.length,1);
  assert((await f.state()).rooms['room-one'].deliveries.every(j=>j.phase==='cancelled'));
 }finally{await f.close();}
});

test('pending room questions keep the actual request identity and owning session',async()=>{
 const f=await fixture();try{
  await f.create();await f.send('@forge Research');await f.idle();const sid=f.prompts[0].sessionId;
  f.pending.set('question-real',{rpcId:'question-real',payload:{type:'question/requested',sessionId:sid,questions:[{id:'q',question:'Which project?'}]}});
  f.pending.set('question-other',{rpcId:'question-other',payload:{type:'question/requested',sessionId:'not-this-room',questions:[]}});
  const log=await f.driver.request('groups.log',{room_id:'room-one'});
  assert.equal(log.requests.length,1);assert.equal(log.requests[0].id,'question-real');assert.equal(log.requests[0].params.session_id,sid);
 }finally{await f.close();}
});

test('malformed durable data is preserved and no conversation is fabricated',async()=>{
 const f=await fixture();try{
  await f.driver.close();await writeFile(path.join(f.home,'niobium-conversations.json'),'{corrupted');await f.restart();
  await assert.rejects(f.driver.request('groups.list'),/original file is preserved/);assert.equal(await readFile(path.join(f.home,'niobium-conversations.json'),'utf8'),'{corrupted');
 }finally{await f.close();}
});

test('a trusted plugin row is added only with messaging access and tools require an owner',async()=>{
 const granted=JSON.parse(composition({permissions:{}},['messaging'],'persona'));
 assert(granted.some(p=>p.name==='/usr/local/lib/niobium-agent.cjs'));
 assert(!JSON.parse(composition({permissions:{}},[],'persona')).some(p=>p.id==='niobium-messaging'));
 const tool=messagingTool(value=>value,{messaging:{messageFrom:()=>assert.fail('No owner')}});
 await assert.rejects(tool.execute({recipient:'user',text:'hello'},{signal:new AbortController().signal}),/owning Pixel/);
});

test('clearing a completed room archives its provider sessions before removing ownership',async()=>{
 const f=await fixture();try{
  await f.create();await f.send();await f.idle();assert.equal(f.sessions.length,2);
  await f.driver.request('groups.clear',{room_id:'room-one'});
  assert.equal(f.sessions.length,0);assert.equal((await f.driver.ownSessionIDs()).size,0);
  assert.equal((await f.driver.request('groups.list')).rooms.length,0);
 }finally{await f.close();}
});
