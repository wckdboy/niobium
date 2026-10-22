import test from 'node:test';
import assert from 'node:assert/strict';
import { classify, makeReport, reconcileIssue, trackedIssue, validRepo } from '../tools/check-hermes-upstream.mjs';

const baseline = { repository: 'NousResearch/hermes-agent', audited_commit: 'a'.repeat(40),
  audited_release: 'v1', contract_prefix: 'tui_gateway/contracts/', tracking_marker: 'niobium-hermes-compatibility-watch' };
const changed = () => makeReport(baseline, 'b'.repeat(40), { files: [], commits: [], total_commits: 0 }, { tag_name: 'v1' });
test('current commit and release need no issue or network writes', async () => {
  const report = makeReport(baseline, baseline.audited_commit, {}, { tag_name: 'v1' });
  assert.equal(report.needsReview, false);
  assert.equal((await reconcileIssue(() => { throw new Error('must not call API'); }, 'wckdboy/niobium', report)).action, 'current');
});
test('a new release is detected even if the branch head is unchanged', () => {
  assert.equal(makeReport(baseline, baseline.audited_commit, {}, { tag_name: 'v2' }).newRelease, true);
});
test('contract and feature changes are separated from documentation', () => {
  const report = makeReport(baseline, 'b'.repeat(40), { files: [
    { filename: 'tui_gateway/contracts/canonical.py', status: 'modified' },
    { filename: 'tools/delegate_task.py', status: 'added' },
    { filename: 'README.md', status: 'modified' }] }, null);
  assert.equal(report.contractCount, 1); assert.equal(report.featureCount, 1);
  assert.match(report.body, /does not prove a breaking change/);
});
test('missing releases are supported', () => assert.equal(changed().newRelease, false));
test('truncated comparisons require full-history review', () => {
  assert.equal(makeReport(baseline, 'b'.repeat(40), { total_commits: 200, commits: [{}] }, null).truncated, true);
});
test('matching a PR body never mistakes it for the tracking issue', () => {
  assert.equal(trackedIssue([{ body: changed().marker, pull_request: {} }], changed().marker), undefined);
});
test('invalid repository paths are rejected', async () => {
  assert(!validRepo('../escape')); assert(!validRepo('owner/repo/extra')); assert(validRepo('owner/repo'));
  await assert.rejects(reconcileIssue(async () => [], '../escape', changed()), /Invalid downstream/);
});
test('first change creates one issue with the marker', async () => {
  const calls = [];
  const result = await reconcileIssue(async (path, options) => {
    calls.push({ path, options }); return options ? { html_url: 'https://github.com/wckdboy/niobium/issues/2' } : [];
  }, 'wckdboy/niobium', changed());
  assert.equal(result.action, 'created'); assert.equal(calls.length, 2);
  assert.equal(calls[1].options.method, 'POST'); assert.match(calls[1].options.body.body, /niobium-hermes-compatibility-watch/);
});
test('repeated observations never spam or reopen an acknowledged issue', async () => {
  const report = changed(); let calls = 0;
  const result = await reconcileIssue(async () => { calls++; return [{ number: 2, body: report.body, state: 'closed' }]; }, 'wckdboy/niobium', report);
  assert.equal(result.action, 'unchanged'); assert.equal(calls, 1);
});
test('new observations update and reopen the same tracking issue', async () => {
  const calls = [];
  const result = await reconcileIssue(async (path, options) => {
    calls.push({ path, options }); return options ? { html_url: 'issue/2' } : [{ number: 2, body: changed().marker + '\nold', state: 'closed' }];
  }, 'wckdboy/niobium', changed());
  assert.equal(result.action, 'updated'); assert.equal(calls[1].options.method, 'PATCH');
  assert.equal(calls[1].options.body.state, 'open'); assert(calls[1].path.endsWith('/2'));
});
test('pagination reaches an existing marker without creating a duplicate', async () => {
  let reads = 0; let writes = 0;
  const result = await reconcileIssue(async (_path, options) => {
    if (options) { writes++; return {}; }
    return ++reads === 1 ? Array.from({ length: 100 }, () => ({ body: 'unrelated' })) : [{ number: 3, body: changed().body }];
  }, 'wckdboy/niobium', changed());
  assert.equal(result.action, 'unchanged'); assert.equal(reads, 2); assert.equal(writes, 0);
});
test('uncertain issue inventory fails closed instead of creating duplicates', async () => {
  await assert.rejects(reconcileIssue(async () => Array.from({ length: 100 }, () => ({ body: 'unrelated' })), 'wckdboy/niobium', changed()), /search limit/);
});
test('classifications and baseline remain unchanged by reporting', () => {
  const original = structuredClone(baseline); changed(); assert.deepEqual(baseline, original);
  assert.equal(classify({ filename: 'docs/API.md' }), 'other');
});
