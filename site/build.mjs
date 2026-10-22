import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const dist = join(root, 'dist');
const pages = JSON.parse(await readFile(join(root, 'pages.json'), 'utf8'));
const layout = await readFile(join(root, 'layout.html'), 'utf8');
const escape = (value) => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;').replaceAll('"', '&quot;');
await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
await cp(join(root, 'public'), dist, { recursive: true });
for (const page of pages) {
  if (!/^[a-z-]*$/.test(page.slug) || !/^[a-z-]+$/.test(page.key)) throw new Error('Invalid page path');
  const url = page.slug ? '/' + page.slug + '/' : '/';
  const navigation = pages.filter((p) => p.slug !== 'legal').map((p) => {
    const href = p.slug ? '/' + p.slug + '/' : '/';
    return '<a href="' + href + '"' + (href === url ? ' aria-current="page"' : '') + '>' +
      (p.slug || 'overview') + '</a>';
  }).join('\n');
  const values = Object.fromEntries(Object.entries(page).map(([key, value]) => [key, escape(value)]));
  values.canonical = 'https://niobium.sh' + url;
  values.navigation = navigation;
  values.content = await readFile(join(root, 'content', page.key + '.html'), 'utf8');
  const html = layout.replace(/\{\{([a-z]+)\}\}/g, (_, key) => {
    if (!(key in values)) throw new Error('Unknown layout key: ' + key);
    return values[key];
  });
  const directory = join(dist, page.slug);
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, 'index.html'), html);
}
await cp(join(root, '..', 'bot-build.md'), join(dist, 'bot-build.md'));
const sitemap = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
  pages.map((p) => '<url><loc>https://niobium.sh/' + (p.slug ? p.slug + '/' : '') + '</loc></url>').join('\n') + '\n</urlset>\n';
await writeFile(join(dist, 'sitemap.xml'), sitemap);
const missing = layout.replace(/\{\{([a-z]+)\}\}/g, (_, key) => ({
  title: 'Page not found — Niobium', description: 'This Niobium page could not be found.',
  canonical: 'https://niobium.sh/404', label: 'niobium / 404', heading: 'this way back',
  navigation: '<a href="/">overview</a>', content: '<p class="lede">That page is not here. <a href="/">Return to Niobium →</a></p>'
}[key] || ''));
await writeFile(join(dist, '404.html'), missing);
console.log('Built ' + pages.length + ' pages with the shared WCKD layout.');
