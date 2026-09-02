import { readFileSync } from 'node:fs';

const [tag, ...extraArgs] = process.argv.slice(2);
if (tag === undefined || extraArgs.length > 0) {
  throw new Error('Usage: validate-release-tag.ts <tag>');
}

const manifest = JSON.parse(
  readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
) as { name: string; version: string };
const stableVersionPattern = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

if (!stableVersionPattern.test(manifest.version)) {
  throw new Error(`Package version must use numeric x.y.z: ${manifest.version}`);
}

const expectedTag = `${manifest.name}@${manifest.version}`;
if (tag !== expectedTag) {
  throw new Error(`Expected release tag ${expectedTag}, got ${tag}`);
}

console.log(`Validated release tag ${tag}`);
