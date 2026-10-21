#!/usr/bin/env node
/**
 * Create (or dry-run) the Niobium v2 Plane project and issues.
 * Requires: PLANE_API_KEY, PLANE_WORKSPACE_SLUG
 * Optional: PLANE_BASE_URL (default https://api.plane.so), PLANE_PROJECT_NAME
 */
import { readFile } from 'node:fs/promises';

const base = (process.env.PLANE_BASE_URL || 'https://api.plane.so').replace(/\/$/, '');
const key = process.env.PLANE_API_KEY;
const workspace = process.env.PLANE_WORKSPACE_SLUG;
const projectName = process.env.PLANE_PROJECT_NAME || 'Niobium v2';
const csv = await readFile(new URL('../plane/issues.csv', import.meta.url), 'utf8');
const [header, ...rows] = csv.trim().split('\n');
const cols = header.split(',');
const issues = rows.map(line => Object.fromEntries(line.split(',').map((v, i) => [cols[i], v])));
const payload = { project: { name: projectName, identifier: 'NIO', description: 'WCKD.ai Niobium v2' }, issues };

if (!key || !workspace) {
  console.log(JSON.stringify({ dryRun: true, reason: 'PLANE_API_KEY / PLANE_WORKSPACE_SLUG not set', ...payload }, null, 2));
  process.exit(0);
}
const headers = { 'X-API-Key': key, 'Content-Type': 'application/json' };
const api = async (path, init = {}) => {
  const res = await fetch(base + path, { ...init, headers: { ...headers, ...(init.headers || {}) } });
  if (!res.ok) throw new Error(`${init.method || 'GET'} ${path}: ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json();
};
const list = await api(`/api/v1/workspaces/${workspace}/projects/`);
const results = list.results || list;
let project = results.find(p => p.name === projectName);
if (!project) {
  project = await api(`/api/v1/workspaces/${workspace}/projects/`, { method: 'POST', body: JSON.stringify(payload.project) });
}
for (const issue of issues) {
  await api(`/api/v1/workspaces/${workspace}/projects/${project.id}/issues/`, {
    method: 'POST',
    body: JSON.stringify({ name: `${issue.id} · ${issue.title}`, description: `${issue.description}\n\nModule: ${issue.module}\nPriority: ${issue.priority}\nEstimate: ${issue.estimate}` })
  });
}
console.log(`created ${issues.length} issues in ${projectName}`);
