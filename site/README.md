# niobium.sh

Static WCKD lab site for Niobium. One stylesheet, no client JavaScript, no third-party requests.

```sh
npm install
npm run build          # public/ + pages/ -> dist/
npm run dry            # wrangler deploy --dry-run
npm run deploy         # needs Cloudflare auth + niobium.sh zone
```

Cloudflare Worker: `niobium-site` (Workers Static Assets), routes `niobium.sh` and
`www.niobium.sh`. Change `wrangler.jsonc` if the zone/domain differs.

Required secrets for deploy: `CLOUDFLARE_API_TOKEN` (Workers Scripts + Workers
Routes + Zone read) and `CLOUDFLARE_ACCOUNT_ID`, or `npx wrangler login`.
