# niobium.sh

Static WCKD family site: shared mono stylesheet, lowercase headings, self-hosted
assets and no client JavaScript.

Run here: `npm ci`, `npm run build`, `npm run check`, `npm run dry`, then
`npm run deploy` with existing Wrangler authentication.

`layout.html` owns the shared shell, `pages.json` the routes, `content/` the page
bodies and `public/` the assets. The build writes `dist/`, sitemap, 404 and the
repository's canonical bot-build.md. The check validates HTML, links and skill
copies.

Cloudflare Worker: `niobium-site`. Domains: [niobium.sh](https://niobium.sh/)
and [www.niobium.sh](https://www.niobium.sh/).
Deployment 2026-10-22: `659476b6-ada8-49cc-b16c-6716535b49f3`.
HTTP and exact deployed-HTML verification passed. Browser visual QA remains
unverified: automatic approval rejected Brave Browser; the in-app browser was
unavailable.
