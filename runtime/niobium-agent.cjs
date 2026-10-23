'use strict';
// Native Niobium adapter for the pinned DSH API. This module never runs in
// child shell/Node commands. Android transports it through a private socket;
// iOS uses the embedded guest's loopback listener.
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const {createMessaging} = require('./niobium-messaging.cjs');
let activeProvider;
const TOOLSETS = ['file', 'terminal', 'web', 'todo', 'skills', 'delegation', 'messaging'];
const textOf = blocks => (blocks || []).filter(b => b.type === 'text').map(b => b.text || '').join('\n');
const profileID = id => id === 'standard' ? 'default' : id;
const presetID = id => !id || id === 'default' ? 'standard' : id;
const slugOK = id => typeof id === 'string' && /^[a-z][a-z0-9-]{1,30}$/.test(id);
const LOCAL_PROVIDERS = [
  {id: 'deepseek-official', name: 'DeepSeek', ref: 'DEEPSEEK_API_KEY', ns: 'llm-deepseek', path: []},
  {id: 'openai', name: 'OpenAI', ref: 'OPENAI_API_KEY', ns: 'llm-pi-ai', path: ['providers', 'openai']},
  {id: 'anthropic', name: 'Anthropic', ref: 'ANTHROPIC_API_KEY', ns: 'llm-pi-ai', path: ['providers', 'anthropic']},
  {id: 'niobium-compatible', name: 'Compatible endpoint', ref: 'NIOBIUM_COMPATIBLE_API_KEY', ns: 'llm-pi-ai', path: ['providers', 'niobium-compatible'], custom: true}
];

/** Value-free provider state and revision-checked edits through the pinned DSH seam. */
function providerSetup(rpc) {
  // pi-ai requires a credential even for unauthenticated compatible servers.
  // Keep its nonsecret placeholder separate from every real provider key.
  const noAuthRef = 'NIOBIUM_COMPATIBLE_NO_AUTH';
  let edits = Promise.resolve();
  const at = (value, parts) => parts.reduce((v, key) => v?.[key], value);
  const endpoint = value => {
    if (typeof value !== 'string' || value.length > 2048) throw new Error('Enter a valid provider endpoint.');
    const url = new URL(value);
    if ((url.protocol !== 'https:' && !(url.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(url.hostname))) ||
        url.username || url.password || url.search || url.hash) throw new Error('Use HTTPS, or a literal loopback HTTP endpoint, without credentials or query parameters.');
    return url.toString().replace(/\/$/, '');
  };
  async function inspect() {
    const [directory, settings, catalog] = await Promise.all([rpc('llm.providers', {}), rpc('settings.describe', {}), rpc('llm.models', {})]);
    const rows = await Promise.all(LOCAL_PROVIDERS.map(async definition => {
      const section = settings.namespaces.find(n => n.ns === definition.ns);
      const profile = at(section?.value, definition.path) || {};
      const noAuth = definition.custom && profile.apiKeyEnv === noAuthRef;
      const ref = noAuth ? definition.ref : profile.apiKeyEnv || definition.ref;
      const supported = !!section && (definition.custom || directory.providers.some(p => p.provider === definition.id && p.settingsNs === definition.ns && JSON.stringify(p.settingsPath) === JSON.stringify(definition.path)));
      const active = directory.providers.some(p => p.provider === definition.id && p.active);
      let models = catalog.groups.find(g => g.id === definition.id)?.models || [];
      if (supported && !active && !definition.custom) models = (await rpc('llm.discoverModels', {settingsNs: definition.ns, provider: definition.id})).models;
      let baseURL = '', advanced = false;
      if (profile.baseURL) { try { baseURL = endpoint(profile.baseURL); } catch { advanced = true; } }
      if (definition.custom && profile.models?.length > 1) advanced = true;
      if (typeof ref !== 'string' || !/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(ref)) advanced = true;
      return {definition, section, profile, ref, supported, active, models, baseURL, advanced, noAuth};
    }));
    const refs = [...new Set([...rows.filter(r => !r.advanced).map(r => r.ref), noAuthRef])];
    const credentials = (await rpc('credentials.describe', {refs})).credentials;
    const selected = settings.namespaces.find(n => n.ns === 'agent-default-model');
    return {rows, settings, selected, value: {writable: settings.writable,
      default: {provider: selected?.value?.provider || '', model: selected?.value?.model || '', revision: selected?.revision ?? -1},
      providers: rows.map(r => ({id: r.definition.id, name: r.definition.name, supported: r.supported, active: r.active,
        key_configured: !r.noAuth && !!credentials[r.ref]?.configured,
        credential_ready: !!credentials[r.noAuth ? noAuthRef : r.ref]?.configured, key_writable: credentials[r.ref]?.writable === true,
        requires_key: !r.definition.custom || (!!r.profile.apiKeyEnv && !r.noAuth), advanced: r.advanced, base_url: r.baseURL,
        revision: r.section?.revision ?? -1, models: r.models.map(m => ({id: m.id, name: m.name || m.id})),
        custom_model: r.definition.custom ? r.profile.models?.[0]?.id || '' : '',
        context_window: r.profile.models?.[0]?.contextWindow || 32768, max_tokens: r.profile.models?.[0]?.maxTokens || 4096}))}};
  }
  async function configure(p) {
    if (!p || typeof p !== 'object' || Array.isArray(p)) throw new Error('Choose valid provider settings.');
    if (Object.keys(p).some(k => !['provider', 'key', 'base_url', 'model', 'context_window', 'max_tokens', 'make_default', 'expected_revision', 'expected_default_revision'].includes(k))) throw new Error('Unsupported provider setting.');
    const definition = LOCAL_PROVIDERS.find(d => d.id === p.provider);
    if (!definition) throw new Error('Choose a supported local provider.');
    const state = await inspect(), row = state.rows.find(r => r.definition === definition);
    if (!state.settings.writable || !row.supported || row.advanced) throw new Error('Edit this provider through advanced DSH settings.');
    if (p.expected_revision !== undefined && p.expected_revision !== row.section.revision) throw new Error('Provider settings changed elsewhere. Refresh before saving.');
    if (p.make_default !== undefined && typeof p.make_default !== 'boolean') throw new Error('Choose whether to use this model for new chats.');
    if (p.make_default && (!state.selected || p.expected_default_revision !== state.selected.revision)) throw new Error('The default model changed elsewhere. Refresh before saving.');
    const key = p.key === undefined ? '' : typeof p.key === 'string' ? p.key.trim() : null;
    if (key === null || key.length > 4096 || /[\s\x00-\x1f\x7f]/.test(key)) throw new Error('Enter an API key without spaces or control characters.');
    const keyInfo = state.value.providers.find(r => r.id === definition.id);
    if (key && !keyInfo.key_writable) throw new Error('This key is supplied by a read-only credential source.');
    if (!definition.custom && !key && !keyInfo.key_configured) throw new Error('Enter this provider’s API key.');
    const noAuth = definition.custom && !key && !keyInfo.key_configured && (!row.profile.apiKeyEnv || row.noAuth);
    const ref = noAuth ? noAuthRef : row.ref;
    if (noAuth && !(await rpc('credentials.describe', {refs: [noAuthRef]})).credentials[noAuthRef]?.writable) throw new Error('Local credential storage is read-only for this endpoint.');
    const ops = [{op: 'set', path: [...definition.path, 'apiKeyEnv'], value: ref}];
    if (p.base_url !== undefined) ops.push(p.base_url === '' && !definition.custom ? {op: 'unset', path: [...definition.path, 'baseURL']} : {op: 'set', path: [...definition.path, 'baseURL'], value: endpoint(p.base_url)});
    if (definition.custom) {
      if (!p.base_url && !row.baseURL) throw new Error('Enter the compatible endpoint URL.');
      if (typeof p.model !== 'string' || !p.model.trim() || p.model.length > 200 || /[\x00-\x1f\x7f]/.test(p.model)) throw new Error('Enter the model ID served by this endpoint.');
      const context = p.context_window, output = p.max_tokens;
      if (!Number.isInteger(context) || context < 512 || context > 2000000 || !Number.isInteger(output) || output < 1 || output > context) throw new Error('Choose valid context and output limits.');
      ops.push({op: 'set', path: [...definition.path, 'api'], value: 'openai-completions'},
        {op: 'set', path: [...definition.path, 'displayName'], value: 'Compatible endpoint'},
        {op: 'set', path: [...definition.path, 'models'], value: [{id: p.model.trim(), contextWindow: context, maxTokens: output}]});
    } else if (p.make_default && !row.models.some(m => m.id === p.model)) throw new Error('Choose a model from this provider’s catalog.');
    let changed;
    try { changed = await rpc('settings.mutate', {ns: definition.ns, ops, expectedRevision: row.section.revision}); }
    catch { throw new Error('Provider settings could not be saved. Refresh before trying again.'); }
    if (key || noAuth) {
      try { await rpc('credentials.set', {ref, value: noAuth ? 'niobium-no-auth' : key}); }
      catch { throw new Error('Provider settings were saved, but the key could not be stored. Refresh before trying again.'); }
    }
    let current;
    const deadline = Date.now() + 3000;
    do {
      current = await inspect();
      const active = current.value.providers.find(r => r.id === definition.id);
      if (active.active && active.models.length) break;
      await new Promise(resolve => setTimeout(resolve, 50));
    } while (Date.now() < deadline);
    const saved = current.value.providers.find(r => r.id === definition.id);
    if (saved.revision !== changed.revision || !saved.active || !saved.models.length || !saved.credential_ready) throw new Error('Provider setup could not be confirmed. Refresh to check its saved state.');
    if (p.make_default) {
      if (!saved.models.some(m => m.id === p.model)) throw new Error('Provider saved, but this model is not available. Choose its default again.');
      let selected;
      try { selected = await rpc('settings.mutate', {ns: 'agent-default-model', expectedRevision: p.expected_default_revision, ops: [
        {op: 'set', path: ['provider'], value: definition.id}, {op: 'set', path: ['model'], value: p.model}, {op: 'unset', path: ['reasoningEffort']}
      ]}); } catch { throw new Error('Provider saved, but its default model could not be changed. Refresh before trying again.'); }
      current = await inspect();
      if (current.value.default.revision !== selected.revision || current.value.default.provider !== definition.id || current.value.default.model !== p.model) throw new Error('Provider saved, but the default changed elsewhere. Refresh before trying again.');
    }
    return {ok: true, ...current.value};
  }
  return {list: async () => (await inspect()).value, configure: p => {
    const operation = edits.then(() => configure(p)); edits = operation.catch(() => {}); return operation;
  }};
}
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

function composition(spec, enabled, briefing, pluginPath = '/usr/local/lib/niobium-agent.cjs') {
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
  if (allowed.has('messaging')) rows.push({id: 'niobium-messaging', name: pluginPath});
  // JSON is valid YAML. No model-generated YAML or plugin names are executed.
  return JSON.stringify(rows, null, 2) + '\n';
}

function createProvider({rpc, postResponse, home = '/root/.dsh', workspace = '/root/workspace', pluginPath}) {
  const providers = providerSetup(rpc);
  const metadataPath = path.join(home, 'niobium-pixels.json');
  let edits = Promise.resolve();
  const pending = new Map();
  const attachments = new Map();
  const approvalRPCs = new Map();
  async function metadata() {
    try {
      const value = JSON.parse(await fs.readFile(metadataPath, 'utf8'));
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Local Pixel metadata needs repair. Its original file has been preserved.');
      return value;
    }
    catch (error) { if (error.code === 'ENOENT') return {}; throw error; }
  }
  async function save(data) {
    await fs.mkdir(home, {recursive: true, mode: 0o700});
    const stage = metadataPath + '.' + crypto.randomUUID();
    try {
      await fs.writeFile(stage, JSON.stringify(data), {mode: 0o600});
      await fs.rename(stage, metadataPath);
    } finally { await fs.rm(stage, {force: true}); }
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
      const raw = result.answers?.[q.id];
      if (Array.isArray(raw)) {
        if (!q.multiSelect || raw.length === 0 || raw.some(v => typeof v !== 'string' || !labels.includes(v))) throw new Error('Choose valid options for this question.');
        return {id: q.id, selected: [...new Set(raw)]};
      }
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
    return {session_id: sessionId, revision: h.events.at(-1)?.event.seq ?? -1, running: s.running,
      messages: omit ? [] : transcript(h.events), history_has_more: h.hasMore,
      history_before_seq: h.events[0]?.event.seq,
      info: {}, prompts: [], inflight: {streaming: s.running, assistant: s.running ? partial.stepText : ''},
      local_stream_state: partial, local_requests: [...pending.values()].filter(p => p.payload.sessionId === sessionId)};
  }
  const messaging = createMessaging({rpc, home, workspace, metadata, pending, respond, frames: streamFrames});
  async function sessionVisibility() {
    const [roomIDs, workspaceInfo] = await Promise.all([messaging.ownSessionIDs(), rpc('workspace.list', {})]);
    // DSH archives workspace membership separately; session.list still returns
    // those attached/cold logs. Never make an archived room a personal DM.
    return {roomIDs, archived: new Set(workspaceInfo.archivedSessionIds || [])};
  }
  async function request(method, p = {}) {
    if (method.startsWith('groups.')) return messaging.request(method, p);
    switch (method) {
      case 'client.capabilities': return {server_requests: true, declines_not_shown: false};
      case 'runtime.describe': return {provider: 'dsh', local_provider_setup: true, session_create: {sources: ['gui']}};
      case 'gateway.capabilities': return {provider: 'dsh', groups: true, cron: false};
      case 'profiles.list': {
        const [catalog, sessions, meta, visibility] = await Promise.all([rpc('agentPreset.list', {}), rpc('session.list', {}), metadata(), sessionVisibility()]);
        const presets = catalog.presets.filter(a => a.id === 'standard' || a.trust === 'user');
        return {profiles: presets.filter(a => !a.broken).map(a => {
          const id = profileID(a.id);
          const all = sessions.items.filter(s => presetID(s.agentPreset) === a.id && s.origin !== 'subagent' && !visibility.archived.has(s.sessionId)).sort((a,b) => b.updatedAt-a.updatedAt);
          const own = all.filter(s => !visibility.roomIDs.has(s.sessionId));
          const last = own[0];
          return {name: id, origin: 'dsh', display_name: a.id === 'standard' ? 'Scout' : a.name || id,
            description: a.id === 'standard' ? 'Research, files and everyday tasks' : a.description || 'Your on-device Pixel',
            provider: 'DSH', is_default: a.id === 'standard', ui_meta: meta[id]?.ui_meta || {},
            enabled_toolsets: meta[id]?.enabled_toolsets || (a.id === 'standard' ? TOOLSETS.filter(t => t !== 'messaging') : []),
            ui_meta_revisions: {niobium: meta[id]?.revision || 0},
            canonical_session: last && {id: last.sessionId, last_active: last.updatedAt / 1000},
            worker_session: all.find(s => s.running) && {id: all.find(s => s.running).sessionId, last_active: Date.now() / 1000}};
        })};
      }
      case 'profiles.describe': return {toolsets: TOOLSETS.map(name => ({name})), skills: [],
        provider: 'DSH', note: 'Local skills are workspace-wide. Local rooms keep separate sessions. Messaging tools require reviewed messaging access.'};
      case 'profiles.configure': {
        // Appearance-only updates use an optimistic revision; agent composition
        // changes go through pixel.create and are never silently acknowledged.
        if (Object.keys(p).some(k => !['name', 'ui_meta', 'ui_meta_expected_revisions'].includes(k))) throw new Error('Edit local tool access when creating a Pixel.');
        if (!slugOK(p.name)) throw new Error('Choose a valid Pixel profile.');
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
            const briefing = String(p.briefing || spec.soul) + '\nUse DSH subagent tools for real delegation. Local Pixels share the app workspace; requested scopes are operating rules, not an OS sandbox. Use @mentions inside Niobium rooms for attributed collaboration. If messaging is granted, message_agent sends real DMs or posts to a room; queued is not completed. Local scheduled jobs remain unavailable.';
            await fs.writeFile(path.join(stage, 'agent.cordis.yml'), composition(spec, p.enabled_toolsets, briefing, pluginPath), {mode: 0o600});
            await fs.writeFile(path.join(stage, 'preset.yml'), JSON.stringify({name: spec.title, description: spec.role}), {mode: 0o600});
            const meta = await metadata();
            const previous = meta[spec.name];
            meta[spec.name] = {revision: 1, enabled_toolsets: p.enabled_toolsets, ui_meta: {'hermes-bots': {title: spec.title, description: spec.role, color: p.accent},
              niobium: {schema_version: 2, pixel_spec: spec, builder: p.builder || 'template'}}};
            await save(meta);
            try { await fs.rename(stage, directory); }
            catch (error) {
              if (previous === undefined) delete meta[spec.name]; else meta[spec.name] = previous;
              await save(meta).catch(() => {});
              throw error;
            }
          } finally { await fs.rm(stage, {recursive: true, force: true}); }
          return {ok: true};
        });
        edits = operation.catch(() => {}); return operation;
      }
      case 'session.list': {
        const visibility = await sessionVisibility();
        const rows = (await rpc('session.list', {})).items.filter(s => s.origin !== 'subagent' && !visibility.roomIDs.has(s.sessionId) && !visibility.archived.has(s.sessionId) && (!p.profile || presetID(p.profile) === presetID(s.agentPreset)));
        // Native session lists cannot request a continuation page yet. Preserve
        // every local chat rather than silently hiding rows beyond their limit.
        return {sessions: rows.sort((a,b) => b.updatedAt-a.updatedAt).map(s => ({id: s.sessionId, session_id: s.sessionId,
          title: s.projections?.values?.title || 'Chat ' + s.sessionId.slice(-6), updated_at: s.updatedAt / 1000, model: 'DSH', running: s.running}))};
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
        attachments.delete(p.session_id);
        const own = await summary(p.session_id);
        if ((await metadata())[profileID(own.agentPreset)]?.enabled_toolsets?.includes('messaging')) await messaging.rememberUserPrompt(p.session_id);
        const receipt = await rpc('session.prompt', {sessionId: p.session_id, mode: 'queue', content: [{type: 'text', text: p.text}, ...(image ? [image] : [])]});
        return {accepted: receipt.accepted};
      }
      case 'session.history': {
        const h = await rpc('session.history', {sessionId: p.session_id, beforeSeq: p.before_seq, maxMessages: 250});
        return {messages: transcript(h.events), has_more: h.hasMore, before_seq: h.events[0]?.event.seq};
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
        return providers.configure({provider: LOCAL_PROVIDERS.find(d => d.ref === p.ref).id, key: p.value});
      }
      case 'local.providers': return providers.list();
      case 'local.provider.configure': return providers.configure(p);
      case 'local.models': return rpc('llm.models', {});
      case 'session.stats': {
        const h = await history(p.session_id);
        const projection = h.projections?.values;
        const usage = projection?.tokenUsage;
        if (!usage) throw new Error('Local usage is not available for this session yet.');
        const input = (usage.uncachedInputTokens || 0) + (usage.cacheReadTokens || 0) + (usage.cacheWriteTokens || 0), output = usage.outputTokens || 0;
        const jobs = await rpc('subagent.list', {parentSessionId: p.session_id});
        const model = await rpc('session.models', {sessionId: p.session_id});
        return {input_tokens: input, output_tokens: output, total_tokens: input + output,
          cache_read_tokens: usage.cacheReadTokens || 0, api_calls: projection.sessionStats?.steps || 0,
          context_tokens: projection.contextPressure?.pressureTokens || 0, context_length: projection.contextPressure?.contextWindow,
          active_subagents: jobs.entries.filter(e => e.kind === 'child' && e.activity === 'running').length, model: model.current.model};
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
  return {request, respond, capture, pending, messaging};
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
    case 'turn/start': state.text = ''; state.stepText = ''; state.lastText = ''; return [notify('message.start', {})];
    case 'step/start': state.stepText = ''; return [];
    case 'assistant/chunk': {
      if (d.chunk?.type === 'reasoning-delta') return [notify('reasoning.delta', {text: d.chunk.text})];
      if (d.chunk?.type !== 'text-delta') return [];
      const text = d.chunk.text;
      state.stepText += d.chunk.text; state.text += text;
      return [notify('message.delta', {text})];
    }
    case 'assistant/message': {
      const text = textOf(d.message?.content);
      if (!state.stepText) state.text += text;
      state.stepText = ''; state.lastText = text;
      return text ? [notify('message.segment', {text})] : [];
    }
    case 'tool/call': return [notify('tool.start', {tool_id: d.callId, name: d.name, args: d.arguments})];
    case 'tool/result': {
      const result = d.message?.content?.find(b => b.type === 'tool-result');
      return [notify('tool.complete', {tool_id: d.message?.source?.callId, result: textOf(result?.content), is_error: result?.isError || false})];
    }
    case 'turn/end': return [notify('message.complete', {text: state.stepText || '', summary: state.lastText || '',
      ...(d.reason?.kind === 'error' ? {error: d.reason.error?.message || 'The local model request failed. Check your provider key under Engines.'} : {})})];
    default: return [];
  }
}

// DSH binds HTTP before registering its event upgrade route. A native client
// may therefore reach our status endpoint while that route still resets its
// socket. Wait only for this initial attachment; never reconnect or replay an
// established stream, whose session state must be recovered by the client.
function connectEventStream({connect, onOpen, onMessage, onFailure, onWaiting = () => {},
  timeoutMs = 10000, retryDelayMs = 100}) {
  let socket, retryTimer, deadlineTimer, stopped = false, opened = false, attempts = 0, lastFailure;
  const stop = () => {
    if (stopped) return;
    stopped = true;
    clearTimeout(retryTimer); clearTimeout(deadlineTimer);
    socket?.terminate();
  };
  const fail = message => { if (stopped) return; stop(); onFailure(message); };
  const attempt = () => {
    if (stopped) return;
    attempts++;
    let own;
    try { own = connect(); } catch { fail('DSH event stream unavailable'); return; }
    socket = own;
    let ended = false;
    const unavailable = (message, retryable) => {
      if (stopped || ended || socket !== own) return;
      ended = true;
      own.terminate();
      if (!opened && retryable) {
        lastFailure = message;
        if (attempts === 1) onWaiting(message);
        retryTimer = setTimeout(attempt, Math.min(retryDelayMs * 2 ** Math.min(attempts - 1, 4), 500));
      } else fail(message);
    };
    own.on('open', () => {
      if (stopped || ended || socket !== own) return;
      opened = true;
      clearTimeout(deadlineTimer);
      onOpen();
    });
    own.on('message', raw => { if (!stopped && !ended && socket === own) onMessage(raw); });
    own.on('unexpected-response', (_request, response) => {
      response.resume();
      unavailable('DSH event stream HTTP ' + response.statusCode, [404, 503].includes(response.statusCode));
    });
    own.on('error', error => {
      // No URLs, headers, exception messages or peer-supplied close reasons.
      const code = /^[A-Z][A-Z0-9_]{0,30}$/.test(error.code || '') ? ' (' + error.code + ')' : '';
      unavailable('DSH event stream unavailable' + code,
        ['ECONNRESET', 'ECONNREFUSED', 'EPIPE', 'ETIMEDOUT'].includes(error.code));
    });
    own.on('close', code => unavailable('DSH event stream closed', !opened && code === 1006));
  };
  deadlineTimer = setTimeout(() => fail(lastFailure ? lastFailure + '; startup timed out' : 'DSH event stream startup timed out'), timeoutMs);
  attempt();
  return stop;
}

function install() {
  const modules = process.env.NIOBIUM_DSH_MODULES || '/usr/local/lib/node_modules';
  const WebSocket = require(path.join(modules, 'ws'));
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
  const provider = createProvider({rpc, postResponse: body => post('respond', body),
    home: process.env.NIOBIUM_DSH_HOME || '/root/.dsh', workspace: process.env.NIOBIUM_WORKSPACE || '/root/workspace',
    pluginPath: process.env.NIOBIUM_DSH_MODULES ? __filename : undefined});
  activeProvider = provider;
  const wss = new WebSocket.WebSocketServer({noServer: true, maxPayload: 12 * 1024 * 1024});
  function authorized(request, url) {
    if (request.headers.origin && request.headers.origin !== 'http://127.0.0.1:' + port) return false;
    const value = url.searchParams.get('token') || (request.headers.authorization || '').replace(/^Bearer /, '');
    const a = Buffer.from(value), b = Buffer.from(key);
    return a.length === b.length && crypto.timingSafeEqual(a, b);
  }
  wss.on('connection', async ws => {
    console.error('[niobium] native provider socket opened');
    let selected = null;
    let attachEpoch = 0, buffering = false, baseline = -1, buffered = [];
    const states = new Map();
    const send = value => { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value)); };
    const stopUpstream = connectEventStream({
      connect: () => new WebSocket('ws://127.0.0.1:' + port + '/api/events.mux', socketPath ? {
        createConnection: () => require('node:net').connect(socketPath)} : {}),
      onMessage: raw => {
      try {
        const full = JSON.parse(raw.toString()); provider.capture(full);
        const sid = full.payload.sessionId;
        if (sid !== selected) return;
        if (buffering) {
          if (buffered.length >= 10000) { ws.close(1011, 'Reopen the chat to restore its latest state'); return; }
          buffered.push(full); return;
        }
        if (!states.has(sid)) states.set(sid, {text: '', stepText: '', priorStep: false});
        streamFrames(full, states.get(sid)).forEach(send);
      } catch { ws.close(1011, 'Invalid DSH event'); }
      },
      onOpen: () => {
        console.error('[niobium] DSH event stream opened');
        send({jsonrpc: '2.0', method: 'event', params: {type: 'gateway.ready', payload: {provider: 'dsh'}}});
      },
      onWaiting: reason => console.error('[niobium] waiting for startup: ' + reason),
      onFailure: reason => {
        console.error('[niobium] ' + reason);
        ws.close(1011, reason);
      }
    });
    ws.on('close', stopUpstream);
    ws.on('error', stopUpstream);
    ws.on('message', async raw => {
      let frame;
      try {
        frame = JSON.parse(raw.toString());
        if (!frame.method) { await provider.respond(frame.id, frame.result || {}, frame.error); return; }
        if (frame.method === 'local.attach') {
          if (frame.params?.session_id !== selected) throw new Error('This local chat selection changed.');
          const ready = buffered; buffered = []; buffering = false;
          send({jsonrpc: '2.0', id: frame.id, result: {ok: true}});
          for (const full of ready) {
            if (full.payload.type === 'session/event' && full.payload.event.seq <= baseline) continue;
            streamFrames(full, states.get(selected)).forEach(send);
          }
          return;
        }
        const attaching = ['session.resume', 'session.create'].includes(frame.method) && !frame.params?.omit_messages;
        const epoch = attaching ? ++attachEpoch : attachEpoch;
        if (attaching) { selected = frame.params?.session_id || null; buffered = []; buffering = true; }
        const result = await provider.request(frame.method, frame.params);
        if (attaching && epoch === attachEpoch) {
          selected = result.session_id; baseline = result.revision;
          states.set(result.session_id, result.local_stream_state);
        }
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

function messagingTool(defineTool, provider) {
  return defineTool({
    name: 'message_agent',
    description: 'Send an attributed DM to another local Pixel or the user, or post to a room you belong to. Use recipient profile ID or user, or provide room_id and @mentions. A queued receipt is not a reply. Discussion has a finite budget; never loop or invent delivery/completion.',
    parameters: {
      recipient: {type: 'string', description: 'Local Pixel profile ID, or user. Omit when posting to room_id.'},
      room_id: {type: 'string', description: 'An existing room you belong to; never guess an ID.'},
      text: {type: 'string', required: true, description: 'A concise message, at most 4,000 characters.'}
    },
    output: {schema: {type: 'object', additionalProperties: false, properties: {
      accepted: {type: 'boolean', required: true}, room_id: {type: 'string', required: true},
      event_id: {type: 'string', required: true}, status: {type: 'string', required: true}
    }}, render: (_args, value) => [{type: 'text', text: JSON.stringify(value)}]},
    async execute(args, exec) {
      if (!exec.agent?.session?.id) throw new Error('Messaging requires an owning Pixel session.');
      return provider.messaging.messageFrom(exec.agent.session.id, args, exec.signal);
    }
  });
}
function directoryTool(defineTool, provider) {
  const requiredString = {type: 'string', required: true};
  return defineTool({
    name: 'list_pixels',
    description: 'List actual local Pixel IDs/handles and rooms this Pixel belongs to before using message_agent. Never guess identities or room IDs.',
    parameters: {},
    output: {
      schema: {
        type: 'object', additionalProperties: false,
        properties: {
          pixels: {type: 'array', required: true, items: {
            type: 'object', additionalProperties: false,
            properties: {id: requiredString, name: requiredString, handle: requiredString}
          }},
          rooms: {type: 'array', required: true, items: {
            type: 'object', additionalProperties: false,
            properties: {room_id: requiredString, name: requiredString,
              members: {type: 'array', required: true, items: {type: 'string'}}}
          }}
        }
      },
      render: (_args, value) => [{type: 'text', text: JSON.stringify(value)}]
    },
    async execute(_args, exec) {
      if (!exec.agent?.session?.id) throw new Error('Pixel directory requires an owning session.');
      return provider.messaging.directoryFrom(exec.agent.session.id, exec.signal);
    }
  });
}
async function apply(ctx) {
  if (!activeProvider) throw new Error('Local messaging is available through the Niobium on-device host.');
  const {defineTool} = await import(require('node:url').pathToFileURL(path.join(process.env.NIOBIUM_DSH_MODULES || '/usr/local/lib/node_modules', '@deepseek-ai/dsh-tools/lib/index.js')).href);
  ctx.tools.register(messagingTool(defineTool, activeProvider));
  ctx.tools.register(directoryTool(defineTool, activeProvider));
}
module.exports = {createProvider, composition, transcript, streamFrames, connectEventStream,
  name: 'niobium-messaging', inject: ['tools'], apply, messagingTool, directoryTool};
if (process.env.NIOBIUM_AGENT_TOKEN && process.argv.some(v => v.endsWith('/dsh/lib/bin.js')) && process.argv.includes('web')) install();
