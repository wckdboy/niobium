# Niobium v2 — Plane

Project: **Niobium v2** (`NIO`)

https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/

- Workspace: `wckd`
- Project ID: `2fbcf987-20de-43dc-80ac-0a6bab7ec03a`
- 24 issues imported from `plane/issues.csv`.
- Status: Foundation/Art/Home/AI builder/model/session/remote DSH/site marked done or in progress; parity backlog remains.

Re-sync (idempotent issue creation is not guaranteed; the script currently creates
new issues on each run):

```sh
set -a; . ~/.config/niobium/secrets.env; set +a
node scripts/plane-bootstrap.mjs
```
