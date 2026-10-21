# Deployments

## Local credentials

Create `/Users/dearwolf/.config/niobium/secrets.env` (outside the repository),
mode `600`:

```sh
mkdir -p ~/.config/niobium
cp .env.example ~/.config/niobium/secrets.env
chmod 600 ~/.config/niobium/secrets.env
$EDITOR ~/.config/niobium/secrets.env
```

Do not paste tokens into chat or commit them. The repo ignores `.env*`,
`.secrets/`, `site/.dev.vars`.

## Plane

```sh
set -a; . ~/.config/niobium/secrets.env; set +a
node scripts/plane-bootstrap.mjs          # creates project + 24 issues
```

`plane/issues.csv` is the source ledger; `PLANE_API_KEY` needs project and issue
write scope, `PLANE_WORKSPACE_SLUG` is the workspace URL slug.

## Cloudflare

```sh
set -a; . ~/.config/niobium/secrets.env; set +a
cd site
npm install
npm run dry                               # already passes
npm run deploy                            # niobium.sh + www.niobium.sh
```

Deployed 2026-10-22; apex verified at `https://niobium.sh/` (200).

`CLOUDFLARE_API_TOKEN` needs Workers Scripts, Workers Routes and Zone read for
the `niobium.sh` zone. `CLOUDFLARE_ACCOUNT_ID` is the account that owns the zone.
The Worker is `niobium-site` in `site/wrangler.jsonc`.
