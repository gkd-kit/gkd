import { spawnSync } from 'node:child_process';
import { readdir, rmdir } from 'node:fs/promises';
import { dirname, join, relative, sep } from 'node:path';

const rootDir = dirname(import.meta.dirname);
let removedCount = 0;

function getIgnoredDirectories(paths: string[]): Set<string> {
  if (paths.length === 0) return new Set();

  const result = spawnSync('git', ['check-ignore', '--verbose', '--stdin', '-z'], {
    cwd: rootDir,
    input: `${paths.join('\0')}\0`,
    encoding: 'utf8',
  });
  if (result.error) throw result.error;
  if (result.status !== 0 && result.status !== 1) {
    throw new Error(`git check-ignore failed: ${result.stderr}`);
  }
  const fields = result.stdout.split('\0');
  const ignored = new Set<string>();
  // Verbose NUL output: source, line, pattern, path. Negated matches are kept.
  for (let index = 0; index + 3 < fields.length; index += 4) {
    if (!fields[index + 2].startsWith('!')) ignored.add(fields[index + 3]);
  }
  return ignored;
}

async function cleanDirectory(directory: string): Promise<void> {
  const entries = await readdir(directory, { withFileTypes: true });
  // Do not enter nested repositories or worktrees, or follow symlinks/junctions.
  if (directory !== rootDir && entries.some((entry) => entry.name === '.git')) {
    return;
  }
  const children = entries
    .filter((entry) => entry.isDirectory() && entry.name !== '.git')
    .map((entry) => join(directory, entry.name));
  const paths = children.map(
    (child) => relative(rootDir, child).split(sep).join('/'),
  );
  const ignored = getIgnoredDirectories(paths);
  for (let index = 0; index < children.length; index++) {
    if (!ignored.has(paths[index])) await cleanDirectory(children[index]);
  }

  if (directory === rootDir) return;
  try {
    // rmdir only removes truly empty directories, including after child cleanup.
    await rmdir(directory);
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (code === 'ENOTEMPTY' || code === 'EEXIST') return;
    throw error;
  }
  removedCount++;
  console.log(`Removed: ${relative(rootDir, directory)}`);
}

await cleanDirectory(rootDir);
console.log(`Removed ${removedCount} empty directories.`);
