'use strict';
// Native Niobium adapter for the pinned DSH API. This module never runs in
// child shell/Node commands. Android transports it through a private socket;
// iOS uses the embedded guest's loopback listener.
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const TOOLSETS = ['file', 'terminal', 'web', 'todo', 'skills', 'delegation'];
const textOf = blocks => (blocks || []).filter(b => b.type === 'text').map(b => b.text || '').join('\n');
const profileID = id => id === 'standard' ? 'default' : id;
const presetID = id => !id || id === 'default' ? 'standard' : id;
const slugOK = id => typeof id === 'string' && /^[a-z][a-z0-9-]{1,30}$/.test(id);
function transcript(entries) {
  return entries.flatMap(({event: e}) => {
    const d = e.data || {};
    const base = {row_id: e.seq, timestamp: e.time / 1000};
    if (e.type === 'user/message') return [{...base, role: 'user', text: textOf(d.content)}];
    if (e.type === 'assistant/message') return [{...base, role: 'assistant', text: textOf(d.message?.content),
      reasoning: (d.message?.content || []).filter(b => b.type === 'reasoning').map(b => b.text).join('\n')}];
    if (e.type === 'tool/result') {
      const result = d.message?.content?.find(b => b.type === 'tool-result');
      return [{...base, role: 'tool', name: d.message?.source?.toolName || 'tool',
        tool_call_id: d.message?.source?.callId, content: textOf(result?.content)}];
    }
    return [];
  });
}

function composition(spec, enabled, briefing) {
  const access = spec.permissions || {};
  const allowed = new Set(enabled.filter(t => TOOLSETS.includes(t)));
  if (!access.network) allowed.delete('web');
  if (access.filesystem === 'none') { allowed.delete('file'); allowed.delete('terminal'); }
  if (!access.shell) allowed.delete('terminal');
  const rows = [
    {id: 'persona', name: '@deepseek-ai/dsh-persona', config: {text: briefing, includeRuntimeContext: true}},
    {id: 'instructions', name: '@deepseek-ai/dsh-agent-instructions', config: {maxBytes: 65536}},
    {id: 'ask-user', name: '@deepseek-ai/dsh-tool-ask-user'}
  ];
  function add(id, name, config) { rows.push({id, name: '@deepseek-ai/' + name, ...(config ? {config} : {})}); }
  if (allowed.has('file')) { add('files', 'dsh-tool-fs'); add('search', 'dsh-tool-fs-search', {sampleOverCapGlobResults: false}); }
  if (allowed.has('terminal')) { add('shell', 'dsh-tool-bash'); add('jobs', 'dsh-tool-jobs'); }
  if (allowed.has('web')) add('web', 'dsh-tool-web', {fetch: false, searchTimeoutMs: 60000});
  if (allowed.has('todo')) add('todo', 'dsh-tool-todo', {allowParallelInProgress: true});
  // Skill files can instruct arbitrary operations. DSH's shared skill registry
  // has no per-preset allowlist, so expose skills only after explicit review.
  if (allowed.has('skills')) { add('skill-files', 'dsh-skill-filesystem'); add('skills', 'dsh-tool-skill'); }
  if (allowed.has('delegation')) {
    add('subagent', 'dsh-tool-subagent', {provider: 'spawn', toolName: 'subagent', backgroundMode: 'continuable'});
    add('subagent-control', 'dsh-tool-subagent-control');
    add('subagent-list', 'dsh-tool-subagent-control/list-agents');
  }
  // JSON is valid YAML. No model-generated YAML or plugin names are executed.
  return JSON.stringify(rows, null, 2) + '\n';
}

function createProvider({rpc, postResponse, home = '/root/.dsh', workspace = '/root/workspace'}) {
  const metadataPath = path.join(home, 'niobium-pixels.json');
  let edits = Promise.resolve();
  const pending = new Map();
  const attachments = new Map();
  const approvalRPCs = new Map();
  async function metadata() {
    try { return JSON.parse(await fs.readFile(metadataPath, 'utf8')); }
    catch (error) { if (error.code === 'ENOENT') return {}; throw error; }
  }
  async function save(data) {
    await fs.mkdir(home, {recursive: true, mode: 0o700});
    const stage = metadataPath + '.' + crypto.randomUUID();
    await fs.writeFile(stage, JSON.stringify(data), {mode: 0o600});
    await fs.rename(stage, metadataPath);
  }
  async function history(sessionId) { return rpc('session.history', {sessionId, maxMessages: 250}); }
  async function summary(sessionId) {
    const rows = (await rpc('session.list', {})).items;
    const found = rows.find(s => s.sessionId === sessionId);
    if (!found) throw new Error('This local session no longer exists.');
    return found;
  }
  function capture(envelope) {
    const f = envelope.payload;
    if (f.type === 'session/subscribed') for (const [id, entry] of pending) {
      if (entry.payload.sessionId === f.sessionId) pending.delete(id);
    }
    if (f.type === 'approval/requested' || f.type === 'question/requested') pending.set(envelope.rpcId, envelope);
    if (f.type === 'approval/requested') {
      approvalRPCs.set(f.sessionId + ':' + f.approvalId, envelope.rpcId);
      if (approvalRPCs.size > 256) approvalRPCs.delete(approvalRPCs.keys().next().value);
    }
    if (f.type === 'approval/resolved') f.resolvedRpcId = approvalRPCs.get(f.sessionId + ':' + f.approvalId);
    if (f.type === 'approval/resolved') for (const [id, p] of pending) {
      if (p.payload.sessionId === f.sessionId && p.payload.approvalId === f.approvalId) pending.delete(id);
    }
    if (f.type === 'question/resolved') pending.delete(f.questionRpcId);
  }
  async function respond(id, result, error) {
    const p = pending.get(String(id));
    if (!p) throw new Error('This request has already been resolved.');
    const f = p.payload;
    let response;
    if (f.type === 'approval/requested') response = {ok: true, value: {sessionId: f.sessionId,
      approvalId: f.approvalId, outcome: !error && result.choice === 'once' ? 'allowed-once' : 'rejected'}};
    else if (error || result.outcome === 'cancelled') response = {ok: false, error: {code: 'cancelled', message: 'Cancelled in Niobium'}};
    else response = {ok: true, value: {sessionId: f.sessionId, answer: {answers: f.questions.map(q => {
      const value = String(result.answers?.[q.id] || '');
      const labels = (q.options || []).map(o => o.label);
      return {id: q.id, selected: labels.includes(value) ? [value] : [], ...(labels.includes(value) ? {} : {custom: value})};
    })}}};
    const receipt = await postResponse({type: 'client-response', rpcId: String(id), result: response});
    if (!receipt.accepted) throw new Error('DSH did not accept that response. Reopen the chat to check its current requests.');
    pending.delete(String(id));
    return {ok: true};
  }
  async function snapshot(sessionId, omit = false) {
    const [s, h] = await Promise.all([summary(sessionId), history(sessionId)]);
    const partial = {text: '', stepText: '', priorStep: false};
    for (const {event} of h.events) streamFrames({payload: {type: 'session/event', sessionId, event}}, partial);
    return {session_id: sessionId, revision: h.events.at(-1)?.event.seq || 0, running: s.running,
      messages: omit ? [] : transcript(h.events), history_has_more: h.hasMore,
      info: {}, prompts: [], inflight: {streaming: s.running, assistant: s.running ? partial.text : ''},
      local_stream_state: partial, local_requests: [...pending.values()].filter(p => p.payload.sessionId === sessionId)};
  }
  async function request(method, p = {}) {
    switch (method) {
      case 'client.capabilities': return {server_requests: true, declines_not_shown: false};
      case 'runtime.describe': return {provider: 'dsh', session_create: {sources: ['gui']}};
      case 'gateway.capabilities': return {provider: 'dsh', groups: false, cron: false};
      case 'groups.capabilities': return {driver: false, methods: []};
      case 'profiles.list': {
        const [catalog, sessions, meta] = await Promise.all([rpc('agentPreset.list', {}), rpc('session.list', {}), metadata()]);
        const presets = catalog.presets.filter(a => a.id === 'standard' || a.trust === 'user');
        return {profiles: presets.filter(a => !a.broken).map(a => {
          const id = profileID(a.id);
          const own = sessions.items.filter(s => presetID(s.agentPreset) === a.id && s.origin !== 'subagent').sort((a,b) => b.updatedAt-a.updatedAt);
          const last = own[0];
          return {name: id, origin: 'dsh', display_name: a.id === 'standard' ? 'Scout' : a.name || id,
            description: a.id === 'standard' ? 'Research, files and everyday tasks' : a.description || 'Your on-device Pixel',
            provider: 'DSH', is_default: a.id === 'standard', ui_meta: meta[id]?.ui_meta || {},
            enabled_toolsets: meta[id]?.enabled_toolsets || (a.id === 'standard' ? TOOLSETS : []),
            ui_meta_revisions: {niobium: meta[id]?.revision || 0},
            canonical_session: last && {id: last.sessionId, last_active: last.updatedAt / 1000},
            worker_session: own.find(s => s.running) && {last_active: Date.now() / 1000}};
        })};
      }
      case 'profiles.describe': return {toolsets: TOOLSETS.map(name => ({name})), skills: [],
        provider: 'DSH', note: 'Local skills are workspace-wide. Connectors and room drivers are configured separately.'};
      case 'profiles.configure': {
        // Appearance-only updates use an optimistic revision; agent composition
        // changes go through pixel.create and are never silently acknowledged.
        if (Object.keys(p).some(k => !['name', 'ui_meta', 'ui_meta_expected_revisions'].includes(k))) throw new Error('Edit local tool access when creating a Pixel.');
        const operation = edits.then(async () => {
          const meta = await metadata();
          const entry = meta[p.name] || {revision: 0, ui_meta: {}};
          if (p.ui_meta_expected_revisions?.niobium !== entry.revision) throw new Error('This Pixel changed elsewhere. Refresh and try again.');
          meta[p.name] = {...entry, revision: entry.revision + 1, ui_meta: {...entry.ui_meta, ...p.ui_meta}};
          await save(meta);
          return {ok: true};
        });
        edits = operation.catch(() => {}); return operation;
      }
      case 'pixel.create': {
        const operation = edits.then(async () => {
          const spec = p.spec;
          if (!spec || !slugOK(spec.name) || spec.name === 'default' || typeof spec.soul !== 'string' || spec.soul.length > 16000 ||
              !Number.isInteger(spec.character?.age) || spec.character.age < 18 || spec.character.age > 120) throw new Error('A valid adult PixelSpec is required.');
          if (!Array.isArray(p.enabled_toolsets) || p.enabled_toolsets.some(t => !TOOLSETS.includes(t))) throw new Error('Unsupported local tool selection.');
          if ((spec.skills || []).length) throw new Error('DSH skills share a workspace. Select no individual skills for a local Pixel.');
          if ((spec.connectors || []).length) throw new Error('Connect local accounts through advanced DSH settings before requesting them.');
          const directory = path.join(home, '.agent-presets', spec.name);
          await fs.mkdir(path.dirname(directory), {recursive: true, mode: 0o700});
          const existing = (await rpc('agentPreset.list', {})).presets;
          if (existing.some(a => a.id === spec.name)) throw new Error('That Pixel name already exists.');
          const stage = path.join(home, '.agent-presets', '.pending-' + crypto.randomUUID());
          await fs.mkdir(stage, {mode: 0o700});
          try {
            const briefing = String(p.briefing || spec.soul) + '\nUse DSH subagent tools for real delegation. Local Pixels share the app workspace; requested scopes are operating rules, not an OS sandbox. Hermes rooms, agent mentions and scheduled jobs are unavailable on this engine.';
            await fs.writeFile(path.join(stage, 'agent.cordis.yml'), composition(spec, p.enabled_toolsets, briefing), {mode: 0o600});
            await fs.writeFile(path.join(stage, 'preset.yml'), JSON.stringify({name: spec.title, description: spec.role}), {mode: 0o600});
            await fs.rename(stage, directory);
            const meta = await metadata();
            meta[spec.name] = {revision: 1, enabled_toolsets: p.enabled_toolsets, ui_meta: {'hermes-bots': {title: spec.title, description: spec.role, color: p.accent},
              niobium: {schema_version: 2, pixel_spec: spec, builder: p.builder || 'template'}}};
            await save(meta);
          } finally { await fs.rm(stage, {recursive: true, force: true}); }
          return {ok: true};
        });
        edits = operation.catch(() => {}); return operation;
      }
      case 'session.list': {
        const rows = (await rpc('session.list', {})).items.filter(s => s.origin !== 'subagent' && (!p.profile || presetID(p.profile) === presetID(s.agentPreset)));
        return {sessions: rows.sort((a,b) => b.updatedAt-a.updatedAt).map(s => ({id: s.sessionId, session_id: s.sessionId,
          title: 'Pixel chat', updated_at: s.updatedAt / 1000, model: 'DSH', running: s.running}))};
      }
      case 'session.create': {
        const made = await rpc('session.create', {cwd: workspace, agentPreset: presetID(p.profile)});
        if (p.title) await rpc('session.rename', {sessionId: made.sessionId, title: p.title});
        return snapshot(made.sessionId);
      }
      case 'session.resume': {
        const s = await summary(p.session_id);
        if (p.profile && presetID(s.agentPreset) !== presetID(p.profile)) throw new Error('This chat belongs to a different Pixel.');
        return snapshot(p.session_id, p.omit_messages);
      }
      case 'image.attach_bytes': {
        if (typeof p.data !== 'string' || p.data.length > 8 * 1024 * 1024 ||
            !['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(p.mime)) throw new Error('Choose an image smaller than 6 MB.');
        if (!attachments.has(p.session_id) && attachments.size >= 4) throw new Error('Send or discard the current images before attaching more.');
        await summary(p.session_id);
        attachments.set(p.session_id, {type: 'image', mediaType: p.mime, data: p.data, name: p.filename || 'image'});
        return {ok: true};
      }
      case 'prompt.submit': {
        const image = attachments.get(p.session_id);
        const receipt = await rpc('session.prompt', {sessionId: p.session_id, mode: 'queue', content: [{type: 'text', text: p.text}, ...(image ? [image] : [])]});
        attachments.delete(p.session_id);
        return {accepted: receipt.accepted};
      }
      case 'session.interrupt': return rpc('session.cancel', {sessionId: p.session_id});
      case 'model.options': {
        const m = await rpc('session.models', {sessionId: p.session_id});
        return {provider: m.current.provider, model: m.current.model,
          providers: m.groups.map(g => ({slug: g.id, name: g.name, is_current: m.current.provider === g.id, models: g.models.map(m => m.id)}))};
      }
      case 'session.mutate': {
        if (p.operation !== 'model') throw new Error('Adjust local reasoning in advanced DSH settings.');
        await rpc('session.selectModel', {sessionId: p.session_id, provider: p.payload.provider, model: p.payload.model});
        return {revision: (await history(p.session_id)).events.at(-1)?.event.seq || 0};
      }
      case 'tools.list': return {toolsets: []}; // Tool access is pinned in the reviewed local preset.
      case 'skills.manage': {
        if (p.action !== 'list') throw new Error('Manage local skills in advanced DSH settings.');
        return {skills: {workspace: []}};
      }
      case 'mcp.servers.list': case 'mcp.servers.status': return {servers: []};
      case 'llm.oneshot': {
        const made = await rpc('session.create', {cwd: workspace, agentPreset: 'minimal'});
        const id = made.sessionId;
        await rpc('session.rename', {sessionId: id, title: 'Pixel designer'});
        try {
          await rpc('session.prompt', {sessionId: id, mode: 'queue', content: [{type: 'text', text: p.instructions + '\n\nUser request:\n' + p.input}]});
          const deadline = Date.now() + 75000;
          while (Date.now() < deadline) {
            await new Promise(resolve => setTimeout(resolve, 400));
            const h = await history(id);
            const ended = h.events.findLast(e => e.event.type === 'turn/end');
            if (!ended) continue;
            const answer = transcript(h.events).findLast(m => m.role === 'assistant' && m.text);
            if (!answer) throw new Error('Configure a model API key under Engines to design a Pixel with AI.');
            return {text: answer.text};
          }
          throw new Error('The Pixel designer timed out.');
        } finally {
          await rpc('session.cancel', {sessionId: id}).catch(() => {});
          await rpc('workspace.archiveSession', {sessionId: id}).catch(() => {});
        }
      }
      case 'local.credential': {
        if (!['DEEPSEEK_API_KEY', 'OPENAI_API_KEY', 'ANTHROPIC_API_KEY'].includes(p.ref) || typeof p.value !== 'string' || !p.value.trim()) throw new Error('Choose a provider and enter its API key.');
        await rpc('credentials.set', {ref: p.ref, value: p.value.trim()}); return {ok: true};
      }
      case 'local.models': return rpc('llm.models', {});
      case 'session.stats': {
        const h = await history(p.session_id);
        const usage = h.events.filter(e => e.event.type === 'assistant/message').map(e => e.event.data.usage || {});
        const input = usage.reduce((n,u) => n + (u.inputTokens || 0), 0), output = usage.reduce((n,u) => n + (u.outputTokens || 0), 0);
        return {input_tokens: input, output_tokens: output, total_tokens: input + output,
          cache_read_tokens: usage.reduce((n,u) => n + (u.cacheReadTokens || 0), 0), api_calls: usage.length};
      }
      case 'subagent.list': {
        const list = await rpc('subagent.list', {parentSessionId: p.session_id});
        return {subagents: list.entries.filter(e => e.kind === 'child').map(e => ({id: e.id, subagent_id: e.id,
          goal: e.label || 'Companion task', status: e.activity === 'running' ? 'running' : 'inactive',
          accepting_steer: e.mode === 'continuable', mode: e.mode}))};
      }
      case 'subagent.tail': case 'subagent.interrupt': case 'subagent.steer': {
        const list = await rpc('subagent.list', {parentSessionId: p.session_id});
        const child = list.entries.find(e => e.kind === 'child' && e.id === p.subagent_id);
        if (!child) throw new Error('This companion no longer belongs to this chat.');
        const payload = {parentSessionId: p.session_id, childSessionId: child.id, mode: child.mode};
        if (method === 'subagent.tail') {
          const h = await rpc('subagent.history', {...payload, maxMessages: 100});
          return {text: transcript(h.events).map(m => m.role + ': ' + (m.text || m.content || '')).join('\n\n')};
        }
        if (child.mode !== 'continuable') throw new Error('This companion is a one-shot task.');
        if (method === 'subagent.interrupt') { await rpc('subagent.interrupt', payload); return {found: true}; }
        await rpc('subagent.prompt', {...payload, content: [{type: 'text', text: p.text}]}); return {status: 'queued'};
      }
      default: throw new Error('This feature requires Hermes or advanced DSH controls: ' + method);
    }
  }
  return {request, respond, capture, pending};
}

function streamFrames(envelope, state) {
  const f = envelope.payload;
  const sid = f.sessionId;
  const notify = (type, payload) => ({jsonrpc: '2.0', method: 'event', params: {type, session_id: sid, payload}});
  if (f.type === 'approval/requested') return [{jsonrpc: '2.0', id: envelope.rpcId, method: 'approval', params: {
    session_id: sid, request_id: envelope.rpcId, tool_name: f.toolName, command: f.toolName,
    description: f.reason || 'DSH requests permission to run this tool.', choices: ['once', 'deny']}}];
  if (f.type === 'question/requested') return [{jsonrpc: '2.0', id: envelope.rpcId, method: 'clarify', params: {session_id: sid,
    questions: f.questions.map(q => ({qid: q.id, question: q.question, choices: (q.options || []).map(o => o.label), multi_select: q.multiSelect || false}))}}];
  if (f.type === 'approval/resolved' || f.type === 'question/resolved') return [notify('request.cancel', {id: f.questionRpcId || f.resolvedRpcId || envelope.rpcId})];
  if (f.type !== 'session/event') return [];
  const e = f.event, d = e.data || {};
  switch (e.type) {
    case 'turn/start': state.text = ''; state.stepText = ''; state.priorStep = false; return [notify('message.start', {})];
    case 'step/start': state.stepText = ''; return [];
    case 'assistant/chunk': {
      if (d.chunk?.type === 'reasoning-delta') return [notify('reasoning.delta', {text: d.chunk.text})];
      if (d.chunk?.type !== 'text-delta') return [];
      const text = (state.priorStep && !state.stepText ? '\n\n' : '') + d.chunk.text;
      state.stepText += d.chunk.text; state.text += text;
      return [notify('message.delta', {text})];
    }
    case 'assistant/message': {
      const text = textOf(d.message?.content);
      const missing = !state.stepText && text ? (state.priorStep ? '\n\n' : '') + text : '';
      state.text += missing; state.priorStep ||= Boolean(text);
      return missing ? [notify('message.delta', {text: missing})] : [];
    }
    case 'tool/call': return [notify('tool.start', {tool_id: d.callId, name: d.name, args: d.arguments})];
    case 'tool/result': {
      const result = d.message?.content?.find(b => b.type === 'tool-result');
      return [notify('tool.complete', {tool_id: d.message?.source?.callId, result: textOf(result?.content), is_error: result?.isError || false})];
    }
    case 'turn/end': return [notify('message.complete', {text: state.text || '',
      ...(d.reason?.kind === 'error' ? {error: d.reason.error?.message || 'The local model request failed. Check your provider key under Engines.'} : {})})];
    default: return [];
  }
}

function install() {
  const WebSocket = require('/usr/local/lib/node_modules/ws');
  const port = Number(process.env.DSH_PORT || 3080);
  const key = process.env.NIOBIUM_AGENT_TOKEN;
  if (!key || key.length < 32) throw new Error('A private native provider token is required.');
  const socketPath = process.env.NIOBIUM_SOCKET;
  const options = {hostname: '127.0.0.1', port, ...(socketPath ? {socketPath} : {})};
  function post(endpoint, body) {
    return new Promise((resolve, reject) => {
      const request = http.request({...options, path: '/api/' + endpoint, method: 'POST',
        headers: {'content-type': 'application/json', host: '127.0.0.1:' + port}}, response => {
        let data = '';
        response.setEncoding('utf8');
        response.on('data', part => { data += part; if (data.length > 16 * 1024 * 1024) request.destroy(new Error('DSH response is too large.')); });
        response.on('end', () => {
          try { if (response.statusCode !== 200) throw new Error('DSH returned HTTP ' + response.statusCode); resolve(JSON.parse(data)); }
          catch (error) { reject(error); }
        });
        response.on('error', reject);
      });
      request.setTimeout(90000, () => request.destroy(new Error('DSH request timed out.')));
      request.on('error', reject); request.end(JSON.stringify(body));
    });
  }
  const rpc = async (method, payload) => {
    const id = crypto.randomUUID();
    const full = await post(method, {type: 'client-request', rpcId: id, method, payload});
    if (full.rpcId !== id) throw new Error('DSH response correlation failed.');
    if (!full.result?.ok) throw new Error(full.result?.error?.message || 'DSH rejected this request.');
    return full.result.value;
  };
  const provider = createProvider({rpc, postResponse: body => post('respond', body)});
  const wss = new WebSocket.WebSocketServer({noServer: true, maxPayload: 12 * 1024 * 1024});
  function authorized(request, url) {
    if (request.headers.origin && request.headers.origin !== 'http://127.0.0.1:' + port) return false;
    const value = url.searchParams.get('token') || (request.headers.authorization || '').replace(/^Bearer /, '');
    const a = Buffer.from(value), b = Buffer.from(key);
    return a.length === b.length && crypto.timingSafeEqual(a, b);
  }
  wss.on('connection', async ws => {
    let selected = null;
    const states = new Map();
    const send = value => { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value)); };
    const upstream = new WebSocket('ws://127.0.0.1:' + port + '/api/events.mux', socketPath ? {
      createConnection: () => require('node:net').connect(socketPath)} : {});
    upstream.on('message', raw => {
      try {
        const full = JSON.parse(raw.toString()); provider.capture(full);
        const sid = full.payload.sessionId;
        if (sid !== selected) return;
        if (!states.has(sid)) states.set(sid, {text: '', stepText: '', priorStep: false});
        streamFrames(full, states.get(sid)).forEach(send);
      } catch { ws.close(1011, 'Invalid DSH event'); }
    });
    upstream.on('open', () => send({jsonrpc: '2.0', method: 'event', params: {type: 'gateway.ready', payload: {provider: 'dsh'}}}));
    upstream.on('error', () => ws.close(1011, 'DSH event stream unavailable'));
    upstream.on('close', () => ws.close(1011, 'DSH event stream closed'));
    ws.on('close', () => upstream.close());
    ws.on('error', () => upstream.close());
    ws.on('message', async raw => {
      let frame;
      try {
        frame = JSON.parse(raw.toString());
        if (!frame.method) { await provider.respond(frame.id, frame.result || {}, frame.error); return; }
        if (frame.method === 'session.resume') selected = frame.params?.session_id || null;
        const result = await provider.request(frame.method, frame.params);
        if (frame.method === 'session.create') selected = result.session_id;
        if (result.local_stream_state) states.set(result.session_id, result.local_stream_state);
        send({jsonrpc: '2.0', id: frame.id, result});
      } catch (error) {
        if (frame?.method) send({jsonrpc: '2.0', id: frame.id, error: {code: -32000, message: error.message}});
        else send({jsonrpc: '2.0', method: 'event', params: {type: 'error', session_id: selected || '', payload: {message: error.message}}});
      }
    });
  });
  const originalEmit = http.Server.prototype.emit;
  http.Server.prototype.emit = function(event, ...args) {
    if (event === 'request' || event === 'upgrade') {
      const [request, response, head] = args;
      const url = new URL(request.url, 'http://127.0.0.1');
      if (url.pathname.startsWith('/niobium/')) {
        if (!authorized(request, url)) {
          if (event === 'upgrade') { response.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n'); }
          else { response.writeHead(403); response.end(); }
        } else if (event === 'upgrade' && url.pathname === '/niobium/api/ws') wss.handleUpgrade(request, response, head, ws => wss.emit('connection', ws));
        else if (event === 'request' && request.method === 'GET' && url.pathname === '/niobium/api/status') {
          response.writeHead(200, {'content-type': 'application/json', 'cache-control': 'no-store'});
          response.end(JSON.stringify({version: 'DSH · on device', provider: 'dsh'}));
        } else if (event === 'request') { response.writeHead(404); response.end(); }
        else response.destroy();
        return true;
      }
    }
    return originalEmit.call(this, event, ...args);
  };
}

module.exports = {createProvider, composition, transcript, streamFrames};
if (process.env.NIOBIUM_AGENT_TOKEN && process.argv.some(v => v.endsWith('/dsh/lib/bin.js')) && process.argv.includes('web')) install();
