#!/usr/bin/env node
/**
 * Reconcile content on known Plane work items. Dry-run is the default.
 * Stable server UUIDs prevent duplicate creation; workflow state stays in Plane.
 * API: https://developers.plane.so/api-reference/v2/work-items/update-work-item
 */
import { readFile } from 'node:fs/promises';

const snapshot = JSON.parse(await readFile(new URL('../plane/snapshot.json', import.meta.url), 'utf8'));
const apply = process.argv.slice(2).includes('--apply');
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
if (!uuid.test(snapshot.project_id)) throw new Error('Invalid project id');
const seen = new Set();
const escapeHTML = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const writes = snapshot.work_items.map(item => {
  if (!uuid.test(item.id) || seen.has(item.id) || !item.name?.trim()) throw new Error('Invalid or duplicate work item');
  seen.add(item.id);
  const fields = { name: item.name, priority: item.priority };
  if (item.description?.trim()) fields.description_html = '<p>' + escapeHTML(item.description) + '</p>';
  return { id: item.id, identifier: 'NIO-' + item.sequence_id, fields };
});

if (!apply) {
  console.log(JSON.stringify({ dryRun: true, project: snapshot.project_id, updates: writes,
    note: 'Use --apply to patch these existing records. This command never creates projects or work items and never changes workflow states.' }, null, 2));
  process.exit(0);
}

const key = process.env.PLANE_API_KEY;
const workspace = process.env.PLANE_WORKSPACE_SLUG;
if (!key || !workspace) throw new Error('--apply requires PLANE_API_KEY and PLANE_WORKSPACE_SLUG');
const base = new URL(process.env.PLANE_BASE_URL || 'https://api.plane.so');
if (base.username || base.password || base.search || base.hash || !['http:', 'https:'].includes(base.protocol)) {
  throw new Error('Invalid Plane API base URL');
}
const prefix = '/api/v2/workspaces/' + encodeURIComponent(workspace) + '/projects/' + snapshot.project_id + '/work-items/';
for (const write of writes) {
  const response = await fetch(new URL(prefix + write.id + '/', base), {
    method: 'PATCH',
    headers: { 'X-API-Key': key, 'Content-Type': 'application/json' },
    body: JSON.stringify(write.fields),
    signal: AbortSignal.timeout(20_000)
  });
  if (!response.ok) throw new Error('Plane update failed for ' + write.identifier + ': HTTP ' + response.status + '. Already completed patches are safe to run again.');
  console.log('Updated ' + write.identifier);
}
