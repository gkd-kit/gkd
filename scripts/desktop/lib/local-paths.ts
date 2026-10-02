import { fileURLToPath } from 'node:url';
import { isAbsolute, join, relative, resolve } from 'node:path';

export const repositoryRoot = fileURLToPath(new URL('../../../', import.meta.url));
export const localRoot = join(repositoryRoot, '.local');
export const desktopProfile = join(localRoot, 'desktop', 'profile');
export const desktopTests = join(localRoot, 'tests', 'desktop');
export const androidTests = join(localRoot, 'tests', 'android');

export function assertDesktopTestPath(path: string) {
  const child = relative(desktopTests, resolve(path));
  if (!child || child === '..' || child.startsWith(`..\\`) || child.startsWith('../') || isAbsolute(child)) {
    throw new Error(`Expected an isolated Desktop test path: ${path}`);
  }
}
