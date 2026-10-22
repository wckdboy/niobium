# Hermes update watch

The daily GitHub workflow checks NousResearch/hermes-agent at 06:17 UTC.
It also supports manual dispatch. Scheduled runs activate on the default
branch after the workflow is merged.

config/hermes-upstream.json pins the audited commit and release.
tools/check-hermes-upstream.mjs compares upstream head, releases, gateway
contracts, CLI and tool changes. It creates or updates one tracking issue;
identical observations cause no new comment or issue. A new observation can
reopen the same issue. Truncated comparisons are called out explicitly.

The checker never executes upstream code, modifies the app or automatically
advances the baseline. API/auth failures fail the workflow instead of claiming
compatibility. The workflow token has read-only contents and issue-write access.

Run a read-only check with a GitHub token in GH_TOKEN or GITHUB_TOKEN:

```sh
node tools/check-hermes-upstream.mjs
```

Use --issue to reconcile the tracking issue. Default downstream is
wckdboy/niobium; GITHUB_REPOSITORY can select an authorized test repository.

For each update: review changed wire contracts and capabilities, update both
native clients, add regression fixtures, run the suite and live provider-backed
acceptance, then update HERMES-PARITY.md and the pinned baseline in the same PR.
A change notification is evidence that review is needed, not proof of a breaking
change or of completed compatibility.
