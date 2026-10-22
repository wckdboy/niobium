import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
const {createProvider, composition, transcript, streamFrames} = createRequire(import.meta.url)('../runtime/niobium-agent.cjs');

test('local tool grants produce only trusted plugin rows and honor denied access', () => {
  const spec = {permissions: {network: false, shell: false, filesystem: 'none'}};
  const rows = JSON.parse(composition(spec, ['file', 'terminal', 'web', 'todo', 'injected-plugin'], 'personality'));
  assert.deepEqual(rows.map(r => r.id), ['persona', 'instructions', 'ask-user', 'todo']);
  assert.equal(rows[0].config.text, 'personality');
});

test('native Pixel creation persists a real DSH composition and appearance atomically', async () => {
  const home = await mkdtemp(path.join(tmpdir(), 'niobium-provider-'));
  const p = createProvider({home, rpc: async () => ({presets: []})});
  const spec = {name: 'night-scout', title: 'Night Scout', role: 'Research', soul: 'Stay curious.',
    permissions: {network: true, shell: false, filesystem: 'workspace'}, character: {age: 30}, skills: [], connectors: []};
  try {
    assert.deepEqual(await p.request('pixel.create', {spec, enabled_toolsets: ['web', 'file'], accent: '#A78BFA'}), {ok: true});
    const rows = JSON.parse(await readFile(path.join(home, '.agent-presets/night-scout/agent.cordis.yml'), 'utf8'));
    assert(rows.some(r => r.name === '@deepseek-ai/dsh-tool-web'));
    assert(!rows.some(r => r.name === '@deepseek-ai/dsh-tool-bash'));
    const meta = JSON.parse(await readFile(path.join(home, 'niobium-pixels.json'), 'utf8'));
    assert.equal(meta['night-scout'].ui_meta.niobium.pixel_spec.character.age, 30);
  } finally { await rm(home, {recursive: true, force: true}); }
});

test('local creation rejects traversal, minors and unimplemented per-skill grants', async () => {
  const p = createProvider({rpc: async () => ({presets: []})});
  await assert.rejects(p.request('pixel.create', {spec: {name: '../escape'}}), /adult PixelSpec/);
  const spec = {name: 'safe-pixel', soul: 'Adult.', character: {age: 17}};
  await assert.rejects(p.request('pixel.create', {spec}), /adult PixelSpec/);
  spec.character.age = 28; spec.skills = ['private-skill'];
  await assert.rejects(p.request('pixel.create', {spec, enabled_toolsets: []}), /skills share a workspace/);
});

test('model picker preserves provider identity and the upstream selection', async () => {
  const p = createProvider({rpc: async () => ({current: {provider: 'openai', model: 'model-a'}, groups: [
    {id: 'openai', name: 'OpenAI', models: [{id: 'model-a'}]}, {id: 'deepseek-official', name: 'DeepSeek', models: [{id: 'model-b'}]}]})});
  const result = await p.request('model.options', {session_id: 'sid'});
  assert.equal(result.providers[0].slug, 'openai');
  assert.equal(result.providers[0].is_current, true);
  assert.equal(result.providers[1].is_current, false);
});

test('recovered approval answers keep DSH correlation and fail on stale requests', async () => {
  let response;
  const p = createProvider({rpc: async () => ({}), postResponse: async value => { response = value; return {accepted: true}; }});
  p.capture({rpcId: 'approval-rpc', payload: {type: 'approval/requested', sessionId: 'sid', approvalId: 'approval-id', toolName: 'bash'}});
  await p.respond('approval-rpc', {choice: 'once'});
  assert.deepEqual(response.result.value, {sessionId: 'sid', approvalId: 'approval-id', outcome: 'allowed-once'});
  await assert.rejects(p.respond('approval-rpc', {choice: 'once'}), /already been resolved/);
});

test('native questions map to DSH option answers and custom text', async () => {
  let response;
  const p = createProvider({rpc: async () => ({}), postResponse: async value => { response = value; return {accepted: true}; }});
  p.capture({rpcId: 'q', payload: {type: 'question/requested', sessionId: 'sid', questions: [{id: 'a', options: [{label: 'Yes'}]}, {id: 'b'}]}});
  await p.respond('q', {answers: {a: 'Yes', b: 'My own answer'}});
  assert.deepEqual(response.result.value.answer.answers, [{id: 'a', selected: ['Yes']}, {id: 'b', selected: [], custom: 'My own answer'}]);
});

test('streaming multiple model steps never duplicates finalized text', () => {
  const state = {text: '', stepText: '', priorStep: false};
  const event = (type, data) => streamFrames({payload: {type: 'session/event', sessionId: 'sid', event: {type, data}}}, state);
  event('turn/start', {}); event('step/start', {});
  assert.equal(event('assistant/chunk', {chunk: {type: 'text-delta', text: 'First'}})[0].params.payload.text, 'First');
  assert.deepEqual(event('assistant/message', {message: {content: [{type: 'text', text: 'First'}]}}), []);
  event('step/start', {}); event('assistant/message', {message: {content: [{type: 'text', text: 'Second'}]}});
  assert.equal(event('turn/end', {reason: {kind: 'completed'}})[0].params.payload.text, 'First\n\nSecond');
});

test('restored DSH transcript keeps user, assistant and tool rows without chunk duplicates', () => {
  const events = [
    {type: 'user/message', data: {content: [{type: 'text', text: 'Hello'}]}},
    {type: 'assistant/chunk', data: {chunk: {type: 'text-delta', text: 'Hi'}}},
    {type: 'assistant/message', data: {message: {content: [{type: 'text', text: 'Hi'}]}}},
    {type: 'tool/result', data: {message: {source: {callId: 'tool1'}, content: [{type: 'tool-result', content: [{type: 'text', text: 'Done'}]}]}}}
  ].map((event, seq) => ({event: {...event, seq, time: 1700000000000}}));
  const rows = transcript(events);
  assert.deepEqual(rows.map(r => r.role), ['user', 'assistant', 'tool']);
  assert.equal(rows[1].text, 'Hi'); assert.equal(rows[2].tool_call_id, 'tool1');
});

test('unsupported Hermes features produce explicit errors, never false success', async () => {
  const p = createProvider({rpc: async () => { throw new Error('Should not reach DSH'); }});
  assert.deepEqual(await p.request('groups.capabilities'), {driver: false, methods: []});
  await assert.rejects(p.request('cron.manage', {action: 'add'}), /requires Hermes/);
  await assert.rejects(p.request('profiles.configure', {name: 'x', enabled_toolsets: ['terminal']}), /Edit local tool access/);
});
