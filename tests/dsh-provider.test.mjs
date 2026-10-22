import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, readFile, writeFile, access, rm} from 'node:fs/promises';
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

test('approval resolved in another client cancels the original native request ID', () => {
  const p = createProvider({rpc: async () => ({})});
  p.capture({rpcId: 'request-id', payload: {type: 'approval/requested', sessionId: 'sid', approvalId: 'approval-id'}});
  const resolved = {rpcId: 'different-broadcast-id', payload: {type: 'approval/resolved', sessionId: 'sid', approvalId: 'approval-id'}};
  p.capture(resolved);
  assert.equal(streamFrames(resolved, {})[0].params.payload.id, 'request-id');
  assert.equal(p.pending.size, 0);
});

test('native questions map to DSH option answers and custom text', async () => {
  let response;
  const p = createProvider({rpc: async () => ({}), postResponse: async value => { response = value; return {accepted: true}; }});
  p.capture({rpcId: 'q', payload: {type: 'question/requested', sessionId: 'sid', questions: [{id: 'a', options: [{label: 'Yes'}]}, {id: 'b'}]}});
  await p.respond('q', {answers: {a: 'Yes', b: 'My own answer'}});
  assert.deepEqual(response.result.value.answer.answers, [{id: 'a', selected: ['Yes']}, {id: 'b', selected: [], custom: 'My own answer'}]);
});

test('multi-select questions preserve every selected label in the DSH response', async () => {
  let response;
  const p = createProvider({rpc: async () => ({}), postResponse: async value => { response = value; return {accepted: true}; }});
  p.capture({rpcId: 'q', payload: {type: 'question/requested', sessionId: 'sid', questions: [{id: 'a', multiSelect: true, options: [{label: 'A'}, {label: 'B'}]}]}});
  await p.respond('q', {answers: {a: ['A', 'B']}});
  assert.deepEqual(response.result.value.answer.answers, [{id: 'a', selected: ['A', 'B']}]);
});

test('metadata failure leaves no installed preset or duplicate-name trap', async () => {
  const home = await mkdtemp(path.join(tmpdir(), 'niobium-provider-'));
  const p = createProvider({home, rpc: async () => ({presets: []})});
  const spec = {name: 'safe-pixel', title: 'Safe', role: 'Research', soul: 'Adult persona.', permissions: {}, character: {age: 30}, skills: [], connectors: []};
  try {
    await writeFile(path.join(home, 'niobium-pixels.json'), '{broken metadata');
    await assert.rejects(p.request('pixel.create', {spec, enabled_toolsets: []}));
    await assert.rejects(access(path.join(home, '.agent-presets/safe-pixel')));
    assert.equal(await readFile(path.join(home, 'niobium-pixels.json'), 'utf8'), '{broken metadata');
    await writeFile(path.join(home, 'niobium-pixels.json'), '{}');
    assert.deepEqual(await p.request('pixel.create', {spec, enabled_toolsets: []}), {ok: true});
  } finally { await rm(home, {recursive: true, force: true}); }
});

test('local session rows retain actual titles and activity without inventing counts', async () => {
  const p = createProvider({rpc: async () => ({items: [
    {sessionId: 'session-a', agentPreset: 'standard', updatedAt: 1700000000000, running: false, projections: {values: {title: 'Research notes'}}},
    {sessionId: 'session-b', agentPreset: 'standard', updatedAt: 1700000001000, running: true, projections: {values: {title: 'Release work'}}}
  ]})});
  const rows = (await p.request('session.list', {profile: 'default'})).sessions;
  assert.deepEqual(rows.map(r => r.title), ['Release work', 'Research notes']);
  assert.equal(rows[0].updated_at, 1700000001);
  assert.equal(rows[0].message_count, undefined);
  assert.equal(rows[0].started_at, undefined);
});

test('native local chat lists retain older sessions beyond the requested display limit', async () => {
  const items = Array.from({length: 120}, (_, i) => ({sessionId: 'chat-' + i, agentPreset: 'standard', updatedAt: i * 1000}));
  items.push({sessionId: 'other-pixel', agentPreset: 'another', updatedAt: 150000},
    {sessionId: 'companion', agentPreset: 'standard', origin: 'subagent', updatedAt: 150000});
  const p = createProvider({rpc: async () => ({items})});
  const rows = (await p.request('session.list', {profile: 'default', limit: 60})).sessions;
  assert.equal(rows.length, 120);
  assert.equal(rows[0].id, 'chat-119');
  assert.equal(rows.at(-1).id, 'chat-0');
});

test('each model step finalizes once and turn completion never repeats its text', () => {
  const state = {text: '', stepText: '', priorStep: false};
  const event = (type, data) => streamFrames({payload: {type: 'session/event', sessionId: 'sid', event: {type, data}}}, state);
  event('turn/start', {}); event('step/start', {});
  assert.equal(event('assistant/chunk', {chunk: {type: 'text-delta', text: 'First'}})[0].params.payload.text, 'First');
  const first = event('assistant/message', {message: {content: [{type: 'text', text: 'First'}]}})[0];
  assert.equal(first.params.type, 'message.segment'); assert.equal(first.params.payload.text, 'First');
  event('step/start', {});
  assert.equal(event('assistant/message', {message: {content: [{type: 'text', text: 'Second'}]}})[0].params.payload.text, 'Second');
  assert.equal(event('turn/end', {reason: {kind: 'completed'}})[0].params.payload.text, '');
});

test('resuming between model steps separates saved replies from unfinished text', async () => {
  const events = [
    {type: 'turn/start', data: {}}, {type: 'step/start', data: {}},
    {type: 'assistant/chunk', data: {chunk: {type: 'text-delta', text: 'First'}}},
    {type: 'assistant/message', data: {message: {content: [{type: 'text', text: 'First'}]}}},
    {type: 'tool/call', data: {callId: 'tool', name: 'read'}},
    {type: 'step/start', data: {}}, {type: 'assistant/chunk', data: {chunk: {type: 'text-delta', text: 'Sec'}}}
  ].map((event, seq) => ({event: {...event, seq, time: 1700000000000}}));
  const p = createProvider({rpc: async method => method === 'session.list' ? {items: [{sessionId: 'sid', agentPreset: 'standard', running: true}]} : {events, hasMore: true}});
  const result = await p.request('session.resume', {session_id: 'sid', profile: 'default'});
  assert.deepEqual(result.messages.filter(m => m.role === 'assistant').map(m => m.text), ['First']);
  assert.equal(result.inflight.assistant, 'Sec'); assert.equal(result.history_before_seq, 0);
  assert.equal(result.local_stream_state.stepText, 'Sec');
});

test('a failed image send cannot silently attach its photo to the next message', async () => {
  let first = true, content;
  const p = createProvider({rpc: async (method, payload) => {
    if (method === 'session.list') return {items: [{sessionId: 'sid'}]};
    content = payload.content;
    if (first) { first = false; throw new Error('Simulated rejected prompt'); }
    return {accepted: true};
  }});
  await p.request('image.attach_bytes', {session_id: 'sid', mime: 'image/png', data: 'fixture'});
  await assert.rejects(p.request('prompt.submit', {session_id: 'sid', text: 'Look'}));
  await p.request('prompt.submit', {session_id: 'sid', text: 'Text only'});
  assert.deepEqual(content, [{type: 'text', text: 'Text only'}]);
});

test('older history requests preserve the upstream cursor and next-page state', async () => {
  let received;
  const p = createProvider({rpc: async (method, payload) => {
    assert.equal(method, 'session.history'); received = payload;
    return {events: [{event: {seq: 40, time: 1700000000000, type: 'user/message', data: {content: [{type: 'text', text: 'Older'}]}}}], hasMore: true};
  }});
  const page = await p.request('session.history', {session_id: 'sid', before_seq: 100});
  assert.deepEqual(received, {sessionId: 'sid', beforeSeq: 100, maxMessages: 250});
  assert.equal(page.before_seq, 40); assert.equal(page.has_more, true); assert.equal(page.messages[0].text, 'Older');
});

test('usage totals come from the full-log projection rather than the history page', async () => {
  const p = createProvider({rpc: async method => {
    if (method === 'session.history') return {events: [], hasMore: true, projections: {values: {
      tokenUsage: {uncachedInputTokens: 1000, outputTokens: 200, cacheReadTokens: 300, cacheWriteTokens: 0},
      sessionStats: {steps: 20}, contextPressure: {pressureTokens: 800, contextWindow: 10000}}}};
    if (method === 'subagent.list') return {entries: [{kind: 'child', activity: 'running'}]};
    return {current: {model: 'fixture-model'}};
  }});
  const stats = await p.request('session.stats', {session_id: 'sid'});
  assert.equal(stats.total_tokens, 1500); assert.equal(stats.api_calls, 20);
  assert.equal(stats.active_subagents, 1); assert.equal(stats.context_length, 10000);
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
