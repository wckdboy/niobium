import test, {afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {createRequire} from 'node:module';
const {createProvider} = createRequire(import.meta.url)('../runtime/niobium-agent.cjs');
const fixtures = [];
afterEach(async () => { for (const f of fixtures.splice(0)) { await f.provider.messaging.close(); await rm(f.home,{recursive:true,force:true}); } });

async function fixture(options = {}) {
  const home = await mkdtemp(path.join(tmpdir(),'nb-providers-'));
  const sections = {
    'llm-deepseek': {apiKeyEnv:'DEEPSEEK_API_KEY',baseURL:'https://api.deepseek.com'},
    'llm-pi-ai': {providers:{}},
    'agent-default-model': {provider:'deepseek-official',model:'deepseek-model',reasoningEffort:'high'}
  };
  const revision = Object.fromEntries(Object.keys(sections).map(k=>[k,0]));
  const keys = {}, calls = [];
  const models = id => [{id:id==='deepseek-official'?'deepseek-model':id==='niobium-compatible'?sections['llm-pi-ai'].providers[id].models[0].id:id+'-model',name:'Fixture '+id}];
  const rpc = async (method,p) => {
    calls.push({method,p:structuredClone(p)});
    const profile = id=>sections['llm-pi-ai'].providers[id];
    if (method==='llm.providers') return {providers:[{provider:'deepseek-official',settingsNs:'llm-deepseek',settingsPath:[],active:true},...['openai','anthropic','niobium-compatible'].map(id=>({provider:id,settingsNs:'llm-pi-ai',settingsPath:['providers',id],active:!!profile(id)&&!options.dormant}))]};
    if (method==='settings.describe') return {writable:true,namespaces:Object.entries(sections).map(([ns,value])=>({ns,value:structuredClone(value),revision:revision[ns]}))};
    if (method==='llm.models') return {groups:['deepseek-official',...Object.keys(sections['llm-pi-ai'].providers)].filter(id=>id==='deepseek-official'||!options.dormant).map(id=>({id,models:models(id)})),failures:[]};
    if (method==='llm.discoverModels') { if(options.discoveryFailure===p.provider) throw new Error('unavailable catalog'); return {models:models(p.provider)}; }
    if (method==='credentials.describe') return {credentials:Object.fromEntries(p.refs.map(ref=>[ref,{configured:!!keys[ref],writable:!options.readonly}]))};
    if (method==='credentials.set') { if(options.keyFailure) throw new Error('do not expose '+p.value); keys[p.ref]=p.value; return {}; }
    if (method==='settings.mutate') {
      if(p.ns==='agent-default-model'&&options.defaultConflict) throw new Error('settings-conflict');
      assert.equal(p.expectedRevision,revision[p.ns]);
      for (const op of p.ops) {
        let at=sections[p.ns];for(const k of op.path.slice(0,-1)) at=at[k]??={};
        if(op.op==='set') at[op.path.at(-1)]=structuredClone(op.value);else delete at[op.path.at(-1)];
      }
      return {revision:++revision[p.ns]};
    }
    throw new Error('Unexpected configuration RPC: '+method);
  };
  const provider=createProvider({rpc,home});
  const f={provider,home,sections,revision,keys,calls};fixtures.push(f);return f;
}

test('provider directory exposes dormant catalogs and no secret-bearing settings',async()=>{
  const f=await fixture();f.sections['llm-pi-ai'].providers.openai={headers:{Authorization:'PRIVATE-HEADER'},apiKeyEnv:'PRIVATE_REF'};f.keys.PRIVATE_REF='PRIVATE-KEY';
  const state=await f.provider.request('local.providers');
  assert.equal(state.providers.find(r=>r.id==='openai').key_configured,true);
  assert.equal(state.providers.find(r=>r.id==='anthropic').active,false);
  assert.equal(state.providers.find(r=>r.id==='anthropic').models[0].id,'anthropic-model');
  for(const secret of ['PRIVATE-HEADER','PRIVATE-KEY','PRIVATE_REF']) assert(!JSON.stringify(state).includes(secret));
});
test('saving an OpenAI key activates its route and preserves other settings',async()=>{
  const f=await fixture();f.sections['llm-pi-ai'].providers.anthropic={modelOverrides:{'keep-me':{maxTokens:10}},headers:{'X-Keep':'untouched'}};
  const before=structuredClone(f.sections['llm-pi-ai'].providers.anthropic);
  const result=await f.provider.request('local.provider.configure',{provider:'openai',key:'fixture-openai',expected_revision:0});
  assert.equal(result.ok,true);assert.equal(result.providers.find(r=>r.id==='openai').active,true);
  assert.deepEqual(f.sections['llm-pi-ai'].providers.anthropic,before);
  assert.equal(f.keys.OPENAI_API_KEY,'fixture-openai');assert.equal(result.default.provider,'deepseek-official');
  assert(!JSON.stringify(result).includes('fixture-openai'));
});
test('legacy mobile credential setup also activates the model route',async()=>{
  const f=await fixture();const result=await f.provider.request('local.credential',{ref:'ANTHROPIC_API_KEY',value:'fixture-anthropic'});
  assert.equal(result.providers.find(r=>r.id==='anthropic').active,true);assert.equal(f.keys.ANTHROPIC_API_KEY,'fixture-anthropic');
});
test('only an explicit model choice changes the default, clearing stale reasoning',async()=>{
  const f=await fixture();const result=await f.provider.request('local.provider.configure',{provider:'openai',key:'fixture-key',model:'openai-model',make_default:true,expected_revision:0,expected_default_revision:0});
  assert.deepEqual(result.default,{provider:'openai',model:'openai-model',revision:1});
  assert.deepEqual(f.sections['agent-default-model'],{provider:'openai',model:'openai-model'});
});
test('stale editors, read-only credentials and invalid endpoints fail before writes',async()=>{
  const f=await fixture({readonly:true});
  for(const p of [{provider:'openai',key:'fixture-key',expected_revision:99},{provider:'openai',key:'fixture-key'},
    {provider:'niobium-compatible',base_url:'http://remote.invalid/v1',model:'fixture',context_window:8192,max_tokens:1024},
    {provider:'__proto__',key:'fixture-key'}]) await assert.rejects(f.provider.request('local.provider.configure',p));
  assert.equal(f.calls.filter(c=>c.method==='settings.mutate'||c.method==='credentials.set').length,0);
});
test('a compatible loopback route supports explicit model limits without a key',async()=>{
  const f=await fixture();const result=await f.provider.request('local.provider.configure',{provider:'niobium-compatible',base_url:'http://127.0.0.1:9000/v1',model:'local-model',context_window:8192,max_tokens:1024,make_default:true,expected_revision:0,expected_default_revision:0});
  assert.equal(result.default.model,'local-model');assert.equal(result.providers.find(r=>r.id==='niobium-compatible').requires_key,false);
  assert.deepEqual(f.calls.filter(c=>c.method==='credentials.set').map(c=>c.p),[{ref:'NIOBIUM_COMPATIBLE_NO_AUTH',value:'niobium-no-auth'}]);
  assert.equal(result.providers.find(r=>r.id==='niobium-compatible').key_configured,false);
  assert.equal(result.providers.find(r=>r.id==='niobium-compatible').credential_ready,true);
  assert.deepEqual(f.sections['llm-pi-ai'].providers['niobium-compatible'].models,[{id:'local-model',contextWindow:8192,maxTokens:1024}]);
});
test('credential storage failure is explicit, redacted, and never selects a default',async()=>{
  const f=await fixture({keyFailure:true});
  await assert.rejects(f.provider.request('local.provider.configure',{provider:'anthropic',key:'PRIVATE-FAILED-KEY',make_default:true,model:'anthropic-model',expected_revision:0,expected_default_revision:0}),error=>!error.message.includes('PRIVATE-FAILED-KEY')&&/key could not be stored/.test(error.message));
  assert.equal(f.calls.filter(c=>c.method==='credentials.set').length,1);assert.equal(f.revision['agent-default-model'],0);
});
test('concurrent legacy setup serializes namespace edits without losing a provider',async()=>{
  const f=await fixture();await Promise.all(['OPENAI_API_KEY','ANTHROPIC_API_KEY'].map(ref=>f.provider.request('local.credential',{ref,value:'fixture-'+ref})));
  assert.equal(f.revision['llm-pi-ai'],2);assert(f.sections['llm-pi-ai'].providers.openai);assert(f.sections['llm-pi-ai'].providers.anthropic);
});
test('an adapter that never activates is not reported ready and does not retry key writes',async()=>{
  const f=await fixture({dormant:true});await assert.rejects(f.provider.request('local.provider.configure',{provider:'openai',key:'fixture-key'}),/could not be confirmed/);
  assert.equal(f.calls.filter(c=>c.method==='credentials.set').length,1);
});
test('invalid endpoints and model limits reject before changing storage',async()=>{
  const f=await fixture();
  for(const base_url of ['http://remote.invalid/v1','https://user:password@example.invalid/v1','https://example.invalid/v1?key=private','https://example.invalid/v1#private'])
    await assert.rejects(f.provider.request('local.provider.configure',{provider:'niobium-compatible',base_url,model:'fixture',context_window:8192,max_tokens:1024}));
  await assert.rejects(f.provider.request('local.provider.configure',{provider:'niobium-compatible',base_url:'http://127.0.0.1:9000/v1',model:'fixture',context_window:8192,max_tokens:10000}));
  assert.equal(f.calls.filter(c=>c.method==='settings.mutate'||c.method==='credentials.set').length,0);
});
test('adding a real compatible key never replaces the no-auth placeholder with a secret',async()=>{
  const f=await fixture();const params={provider:'niobium-compatible',base_url:'http://127.0.0.1:9000/v1',model:'fixture',context_window:8192,max_tokens:1024};
  await f.provider.request('local.provider.configure',params);
  const result=await f.provider.request('local.provider.configure',{...params,key:'fixture-compatible-real',expected_revision:1});
  assert.equal(f.keys.NIOBIUM_COMPATIBLE_NO_AUTH,'niobium-no-auth');
  assert.equal(f.keys.NIOBIUM_COMPATIBLE_API_KEY,'fixture-compatible-real');
  assert.equal(f.sections['llm-pi-ai'].providers['niobium-compatible'].apiKeyEnv,'NIOBIUM_COMPATIBLE_API_KEY');
  assert.equal(result.providers.find(p=>p.id==='niobium-compatible').key_configured,true);
});
test('a concurrent default change preserves the saved provider and asks for a fresh review',async()=>{
  const f=await fixture({defaultConflict:true});
  await assert.rejects(f.provider.request('local.provider.configure',{provider:'openai',key:'fixture-key',model:'openai-model',make_default:true,expected_revision:0,expected_default_revision:0}),/Provider saved, but its default model could not be changed/);
  assert(f.sections['llm-pi-ai'].providers.openai);assert.equal(f.keys.OPENAI_API_KEY,'fixture-key');assert.equal(f.sections['agent-default-model'].provider,'deepseek-official');
});
test('compatible model IDs are normalized consistently before saving and selecting a default',async()=>{
  const f=await fixture();const result=await f.provider.request('local.provider.configure',{provider:'niobium-compatible',base_url:'http://127.0.0.1:9000/v1',model:'  local-model  ',context_window:8192,max_tokens:1024,make_default:true,expected_revision:0,expected_default_revision:0});
  assert.equal(result.default.model,'local-model');
  assert.equal(f.sections['llm-pi-ai'].providers['niobium-compatible'].models[0].id,'local-model');
});
test('editing a no-auth endpoint never reactivates a previously stored real key',async()=>{
  const f=await fixture();const params={provider:'niobium-compatible',base_url:'http://127.0.0.1:9000/v1',model:'fixture',context_window:8192,max_tokens:1024};
  await f.provider.request('local.provider.configure',params);
  f.keys.NIOBIUM_COMPATIBLE_API_KEY='inactive-private-key';
  const result=await f.provider.request('local.provider.configure',{...params,model:'changed-model',expected_revision:1});
  assert.equal(f.sections['llm-pi-ai'].providers['niobium-compatible'].apiKeyEnv,'NIOBIUM_COMPATIBLE_NO_AUTH');
  assert.equal(f.keys.NIOBIUM_COMPATIBLE_API_KEY,'inactive-private-key');
  assert.equal(result.providers.find(p=>p.id==='niobium-compatible').key_configured,false);
  assert(!JSON.stringify(result).includes('inactive-private-key'));
});
test('advanced compatible model fields remain intact and cannot be overwritten by the native editor',async()=>{
  const f=await fixture();const profile={apiKeyEnv:'NIOBIUM_COMPATIBLE_API_KEY',baseURL:'http://127.0.0.1:9000/v1',models:[{id:'custom-model',contextWindow:8192,maxTokens:1024,name:'Custom model',reasoningEfforts:{high:'high'}}]};
  f.sections['llm-pi-ai'].providers['niobium-compatible']=structuredClone(profile);
  const result=await f.provider.request('local.providers');assert.equal(result.providers.find(p=>p.id==='niobium-compatible').advanced,true);
  await assert.rejects(f.provider.request('local.provider.configure',{provider:'niobium-compatible',base_url:'http://127.0.0.1:9001/v1',model:'replacement',context_window:8192,max_tokens:1024}),/advanced DSH settings/);
  assert.deepEqual(f.sections['llm-pi-ai'].providers['niobium-compatible'],profile);
  assert.equal(f.calls.filter(c=>c.method==='settings.mutate'||c.method==='credentials.set').length,0);
});
test('a failed dormant catalog does not block other providers or legacy key activation',async()=>{
  const f=await fixture({discoveryFailure:'openai'});const catalog=await f.provider.request('local.providers');
  assert.deepEqual(catalog.providers.find(p=>p.id==='openai').models,[]);
  assert(catalog.providers.find(p=>p.id==='anthropic').models.length);
  const result=await f.provider.request('local.credential',{ref:'DEEPSEEK_API_KEY',value:'fixture-deepseek'});
  assert.equal(result.providers.find(p=>p.id==='deepseek-official').credential_ready,true);
  assert.equal(f.keys.DEEPSEEK_API_KEY,'fixture-deepseek');
});
test('compatible profiles using another wire protocol remain managed by advanced settings',async()=>{
  for(const api of ['openai-responses','anthropic-messages']) {
    const f=await fixture();const profile={api,baseURL:'http://127.0.0.1:9000/v1',models:[{id:'custom-model',contextWindow:8192,maxTokens:1024}]};
    f.sections['llm-pi-ai'].providers['niobium-compatible']=structuredClone(profile);
    const state=await f.provider.request('local.providers');assert.equal(state.providers.find(p=>p.id==='niobium-compatible').advanced,true);
    await assert.rejects(f.provider.request('local.provider.configure',{provider:'niobium-compatible',model:'replacement',context_window:8192,max_tokens:1024}),/advanced DSH settings/);
    assert.deepEqual(f.sections['llm-pi-ai'].providers['niobium-compatible'],profile);
    assert.equal(f.calls.filter(c=>c.method==='settings.mutate'||c.method==='credentials.set').length,0);
  }
});
test('editing a compatible endpoint preserves its existing display name',async()=>{
  const f=await fixture();const params={provider:'niobium-compatible',base_url:'http://127.0.0.1:9000/v1',model:'fixture',context_window:8192,max_tokens:1024};
  await f.provider.request('local.provider.configure',params);
  f.sections['llm-pi-ai'].providers['niobium-compatible'].displayName='My local lab';
  await f.provider.request('local.provider.configure',{...params,model:'changed-model',expected_revision:1});
  assert.equal(f.sections['llm-pi-ai'].providers['niobium-compatible'].displayName,'My local lab');
});
