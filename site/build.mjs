import { cp, mkdir, rm } from 'node:fs/promises';
await rm('dist', { recursive: true, force: true });
await mkdir('dist', { recursive: true });
await cp('public', 'dist', { recursive: true });
await cp('index.html', 'dist/index.html');
for (const page of ['system', 'local', 'build', 'releases', 'legal']) {
  await cp(`pages/${page}`, `dist/${page}`, { recursive: true });
}
await cp('public/_headers', 'dist/_headers');
console.log('built dist/');
