#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const exec = promisify(execFile);
const root = fileURLToPath(new URL('../', import.meta.url));
const snapshot = JSON.parse(await readFile(new URL('../plane/snapshot.json', import.meta.url), 'utf8'));
const environment = { ...process.env, PLANE_API_KEY: '', PLANE_WORKSPACE_SLUG: '' };
const preview = await exec(process.execPath, ['scripts/plane-bootstrap.mjs'], { cwd: root, env: environment });
assert.equal(JSON.parse(preview.stdout).dryRun, true);
assert.equal(JSON.parse(preview.stdout).updates.length, snapshot.work_items.length);
await assert.rejects(exec(process.execPath, ['scripts/plane-bootstrap.mjs', '--apply'], { cwd: root, env: environment }));

const requests = [];
const server = createServer(async (request, response) => {
  const parts = [];
  for await (const chunk of request) parts.push(chunk);
  requests.push({ method: request.method, path: request.url, auth: request.headers['x-api-key'],
    body: JSON.parse(Buffer.concat(parts).toString()) });
  response.writeHead(200, { 'Content-Type': 'application/json' });
  response.end('{}');
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
try {
  const env = { ...environment, PLANE_API_KEY: 'local-test-key', PLANE_WORKSPACE_SLUG: 'test-workspace',
    PLANE_BASE_URL: 'http://127.0.0.1:' + server.address().port };
  await exec(process.execPath, ['scripts/plane-bootstrap.mjs', '--apply'], { cwd: root, env });
  await exec(process.execPath, ['scripts/plane-bootstrap.mjs', '--apply'], { cwd: root, env });
  assert.equal(requests.length, snapshot.work_items.length * 2);
  const expectedPaths = new Set(snapshot.work_items.map(item =>
    '/api/v2/workspaces/test-workspace/projects/' + snapshot.project_id + '/work-items/' + item.id + '/'));
  for (const request of requests) {
    assert.equal(request.method, 'PATCH');
    assert.equal(request.auth, 'local-test-key');
    assert(expectedPaths.has(request.path));
    assert(Object.keys(request.body).every(key => ['name', 'priority', 'description_html'].includes(key)));
    assert.equal(typeof request.body.name, 'string');
  }
  assert.deepEqual(requests.slice(0, snapshot.work_items.length), requests.slice(snapshot.work_items.length));
  console.log('PASS: dry-run, missing credentials, stable UUID patches, repeat runs and workflow preservation');
} finally {
  await new Promise(resolve => server.close(resolve));
}
