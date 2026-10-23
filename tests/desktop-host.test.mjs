import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, copyFile, symlink, writeFile, rm, stat} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {createRequire} from 'node:module';
const require = createRequire(import.meta.url);
const {start} = require('../desktop/runtime/desktop-host.cjs');

async function fixture() {
  const directory = await mkdtemp(path.join(tmpdir(), 'nb-host-test-'));
  const root = path.join(directory, 'runtime');
  await mkdir(path.join(root, 'node_modules/@deepseek-ai/dsh/lib'), {recursive: true});
  await symlink(path.dirname(require.resolve('../runtime/node_modules/ws/package.json')), path.join(root, 'node_modules/ws'));
  for (const name of ['niobium-agent.cjs', 'niobium-messaging.cjs']) await copyFile(new URL('../runtime/' + name, import.meta.url), path.join(root, name));
  await copyFile(new URL('../android/runtime/niobium-local.cjs', import.meta.url), path.join(root, 'niobium-local.cjs'));
  await writeFile(path.join(root, 'node_modules/@deepseek-ai/dsh/lib/bin.js'), `require('node:http').createServer((req,res)=>{res.writeHead(404);res.end()}).listen(3080,'127.0.0.1');`);
  let runtime;
  try {
    runtime = await start({root, home: path.join(directory, 'home'), workspace: path.join(directory, 'workspace')});
    return {directory, runtime, close: async () => { await runtime.stop(); await rm(directory, {recursive:true,force:true}); }};
  } catch (error) { await runtime?.stop(); await rm(directory, {recursive:true,force:true}); throw error; }
}

test('desktop proxy requires its secret, exact host and same-origin requests', async () => {
  const f = await fixture();
  try {
    const endpoint = f.runtime.origin + '/api/status';
    assert.equal((await fetch(endpoint)).status, 403);
    assert.equal((await fetch(endpoint, {headers:{authorization:'Bearer wrong'}})).status, 403);
    assert.equal((await fetch(endpoint, {headers:{authorization:'Bearer '+f.runtime.token, origin:'https://unrelated.example'}})).status, 403);
    const wrongHost = await new Promise((resolve,reject)=>{const request=http.get(endpoint,{headers:{authorization:'Bearer '+f.runtime.token,host:'unrelated.example'}}, response=>{response.resume();resolve(response.statusCode)});request.on('error',reject)});
    assert.equal(wrongHost,403);
    const valid = await fetch(endpoint, {headers:{authorization:'Bearer '+f.runtime.token}});
    assert.equal(valid.status, 200); assert.equal((await valid.json()).provider, 'dsh');
    assert.equal((await fetch(f.runtime.origin.replace('/niobium','')+'/api/status?token='+f.runtime.token)).status, 403);
    assert.equal((await fetch(endpoint,{method:'POST',headers:{authorization:'Bearer '+f.runtime.token}})).status,403);
  } finally { await f.close(); }
});
test('desktop DSH socket is private and Stop releases owned processes and files', async () => {
  const f = await fixture();
  const {socketPath, child} = f.runtime;
  try {
    assert((await stat(socketPath)).isSocket());
    assert.equal((await stat(path.dirname(socketPath))).mode & 0o777,0o700);
    assert.equal((await stat(path.join(f.directory,'home'))).mode & 0o777,0o700);
    await f.runtime.stop();
    assert.throws(()=>process.kill(child.pid,0),{code:'ESRCH'});
    await assert.rejects(stat(socketPath),{code:'ENOENT'});
    await assert.rejects(fetch(f.runtime.origin+'/api/status'),/fetch failed/);
  } finally { await f.close(); }
});
test('desktop startup cancellation removes its child and private socket', async () => {
  const f = await fixture();
  await f.close();
  const controller = new AbortController(); controller.abort();
  const root = path.resolve('desktop/runtime');
  const directory = await mkdtemp(path.join(tmpdir(),'nb-cancel-test-'));
  try { await assert.rejects(start({root,home:path.join(directory,'home'),workspace:path.join(directory,'workspace'),signal:controller.signal}),/cancelled/); }
  finally { await rm(directory,{recursive:true,force:true}); }
});
