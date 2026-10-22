import { readFile, readdir, stat } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const dist = join(root, 'dist');
const pages = JSON.parse(await readFile(join(root, 'pages.json'), 'utf8'));
const files = ['404.html', ...pages.map((p) => p.slug ? p.slug + '/index.html' : 'index.html')];
const errors = [];
let links = 0;
for (const file of files) {
  const html = await readFile(join(dist, file), 'utf8');
  if ((html.match(/<h1[ >]/g) || []).length !== 1) errors.push(file + ': needs one h1');
  if (!html.includes('id="main"')) errors.push(file + ': missing skip-link target');
  if (/<script[ >]/i.test(html)) errors.push(file + ': unexpected client JavaScript');
  if (/\{\{[a-z]+\}\}/.test(html)) errors.push(file + ': unresolved template');
  const ids = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
  for (const match of html.matchAll(/\b(?:href|src)="([^"]+)"/g)) {
    const href = match[1];
    if (/^(https?:|mailto:|data:)/.test(href)) continue;
    if (href.startsWith('#')) {
      if (!ids.has(href.slice(1))) errors.push(file + ': missing anchor ' + href);
      continue;
    }
    const path = href.split(/[?#]/)[0];
    if (!path.startsWith('/')) { errors.push(file + ': expected root-relative link ' + href); continue; }
    const target = resolve(dist, '.' + path, path.endsWith('/') ? 'index.html' : '');
    if (!target.startsWith(dist + '/')) { errors.push(file + ': escaped output path'); continue; }
    try { if (!(await stat(target)).isFile()) errors.push(file + ': non-file ' + href); }
    catch { errors.push(file + ': broken link ' + href); }
    links++;
  }
}
const master = await readFile(join(root, '..', 'bot-build.md'), 'utf8');
for (const file of ['skills/bot-build.md', 'ios/Resources/bot-build.md', 'android/app/src/main/assets/bot_build.md']) {
  if ((await readFile(join(root, '..', file), 'utf8')) !== master) errors.push('Skill copy drift: ' + file);
}
if (errors.length) throw new Error(errors.join('\n'));
console.log('PASS: ' + files.length + ' HTML pages, ' + links + ' local links/assets, shared skill copies.');

