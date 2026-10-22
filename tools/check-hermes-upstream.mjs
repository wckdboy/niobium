#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const validRepo = value => /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(value) &&
  value.split('/').every(part => part !== '.' && part !== '..');
export function classify(file, prefix = 'tui_gateway/contracts/') {
  if (file.filename.startsWith(prefix)) return 'contract';
  if (/^(gateway\/|tui_gateway\/|tools\/|hermes_cli\/|hermes_state)/.test(file.filename)) return 'feature';
  return 'other';
}
export function makeReport(baseline, head, comparison, release) {
  const files = comparison.files ?? [];
  const contracts = files.filter(file => classify(file, baseline.contract_prefix) === 'contract');
  const features = files.filter(file => classify(file, baseline.contract_prefix) === 'feature');
  const changed = head !== baseline.audited_commit;
  const newRelease = Boolean(release?.tag_name && release.tag_name !== baseline.audited_release);
  const marker = '<!-- ' + baseline.tracking_marker + ' -->';
  const signature = '<!-- observed: ' + head + ' ' + (release?.tag_name ?? 'none') + ' -->';
  const compareURL = 'https://github.com/' + baseline.repository + '/compare/' + baseline.audited_commit + '...' + head;
  const list = rows => rows.slice(0, 30).map(file =>
    '- ' + file.status + ': ' + file.filename.replaceAll('<', '&lt;')).join('\n') || '- None returned.';
  const truncated = files.length >= 300 || (comparison.total_commits ?? 0) > (comparison.commits?.length ?? 0);
  const body = [
    marker, signature, '# Hermes compatibility review',
    'Niobium tested baseline: ' + baseline.audited_commit + '. Observed upstream: ' + head + '.',
    '[Compare the changes](' + compareURL + ').',
    release?.tag_name ? 'Latest published release: ' + release.tag_name + '.' : 'No published release returned.',
    '## Gateway contracts', list(contracts),
    contracts.length ? 'Contract edits can add required fields, remove methods or change result shapes. Review their semantics; a changed file alone does not prove a breaking change.' : '',
    '## Gateway, CLI and tool changes', list(features),
    truncated ? '**The comparison may be truncated. Review the complete history before declaring compatibility.**' : '',
    '## Acceptance checklist',
    '- [ ] Review added/removed RPC methods, required parameters, result schemas, events and capabilities.',
    '- [ ] Update iOS and Android together; preserve session, revision and execution-generation fences.',
    '- [ ] Add changed-wire fixtures and run the complete test suite.',
    '- [ ] Verify Bot Mode, rooms, approvals, credentials, companions and provider-backed execution.',
    '- [ ] Update docs/HERMES-PARITY.md and the baseline only after validation.',
    'This watcher maintains one issue. It never executes upstream code, upgrades the app or advances the tested baseline automatically.'
  ].filter(Boolean).join('\n\n');
  return { changed, newRelease, needsReview: changed || newRelease, contractCount: contracts.length,
    featureCount: features.length, truncated, body, signature, marker, title: 'Hermes compatibility: upstream changes to review' };
}
export function trackedIssue(issues, marker) {
  return issues.find(issue => !issue.pull_request && typeof issue.body === 'string' && issue.body.includes(marker));
}
export async function reconcileIssue(api, target, report) {
  if (!report.needsReview) return { action: 'current' };
  if (!validRepo(target)) throw new Error('Invalid downstream repository');
  let existing;
  for (let page = 1; page <= 10; page++) {
    const rows = await api('/repos/' + target + '/issues?state=all&per_page=100&page=' + page);
    existing = trackedIssue(rows, report.marker);
    if (existing || rows.length < 100) break;
    if (page === 10) throw new Error('Issue search limit reached; refusing a possible duplicate');
  }
  if (existing?.body.includes(report.signature)) return { action: 'unchanged', url: existing.html_url };
  const fields = { title: report.title, body: report.body };
  const path = '/repos/' + target + '/issues' + (existing ? '/' + existing.number : '');
  const result = await api(path, { method: existing ? 'PATCH' : 'POST', body: existing ? { ...fields, state: 'open' } : fields });
  return { action: existing ? 'updated' : 'created', url: result.html_url };
}
export function githubAPI(token) {
  return async (path, options = {}) => {
    if (!path.startsWith('/') || path.startsWith('//')) throw new Error('Invalid GitHub API path');
    const response = await fetch('https://api.github.com' + path, {
      method: options.method ?? 'GET',
      headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'niobium-hermes-watch',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: AbortSignal.timeout(20_000)
    });
    if (options.allow404 && response.status === 404) return null;
    if (!response.ok) throw new Error('GitHub API ' + response.status + ' for ' + path);
    return response.json();
  };
}
async function main() {
  const baseline = JSON.parse(await readFile(new URL('../config/hermes-upstream.json', import.meta.url), 'utf8'));
  if (!validRepo(baseline.repository) || !/^[a-f0-9]{40}$/.test(baseline.audited_commit)) throw new Error('Invalid Hermes baseline');
  const api = githubAPI(process.env.GH_TOKEN || process.env.GITHUB_TOKEN);
  const repo = '/repos/' + baseline.repository;
  const branch = await api(repo + '/branches/' + encodeURIComponent(baseline.branch));
  const head = branch.commit.sha;
  if (!/^[a-f0-9]{40}$/.test(head)) throw new Error('Invalid upstream head');
  const release = await api(repo + '/releases/latest', { allow404: true });
  const comparison = head === baseline.audited_commit ? { files: [], commits: [], total_commits: 0 } :
    await api(repo + '/compare/' + baseline.audited_commit + '...' + head + '?per_page=100');
  const report = makeReport(baseline, head, comparison, release);
  console.log(report.body);
  if (process.argv.includes('--issue')) {
    if (!(process.env.GH_TOKEN || process.env.GITHUB_TOKEN)) throw new Error('--issue needs a repository-scoped GitHub token');
    console.log(JSON.stringify(await reconcileIssue(api, process.env.GITHUB_REPOSITORY || 'wckdboy/niobium', report)));
  } else console.log('Read-only check. Pass --issue to reconcile the tracking issue.');
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
