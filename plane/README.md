# Niobium v2 in Plane

[Niobium project](https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/)
in workspace `wckd`, identifier `NIO`, timezone Europe/Copenhagen.

The 2026-10-22 reconciliation covers 37 work items, including explicit work for
companions, connectors, memory, projects, Android local DSH, admissions,
credentials, quality, Bot Mode, parity, replay and personas. Missing descriptions
and priorities from the old CSV import have been restored.

`snapshot.json` records server UUIDs and observed states; `issues.csv` is a
correctly quoted export. Plane owns workflow state. The site ticket is complete;
native work retains unverified live acceptance gates. See
[HERMES-PARITY.md](../docs/HERMES-PARITY.md).

`node scripts/plane-bootstrap.mjs` previews content reconciliation. The script
never creates projects/work items or changes states, assignments or dates.
With `PLANE_API_KEY` and `PLANE_WORKSPACE_SLUG`, `--apply` patches the known UUIDs
using [Plane's partial-update API](https://developers.plane.so/api-reference/v2/work-items/update-work-item).
Optional `PLANE_BASE_URL` supports self-hosting. Actual project updates used the
connected Plane tools. The script is verified against a local test server.
