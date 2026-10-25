import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { repositoryRoot } from './local-paths.ts';

const exec = promisify(execFile);

/** Build the independent fixture with the shared GKD signing configuration. */
export async function buildAndroidQueryFixture() {
  const windows = process.platform === 'win32';
  await exec(windows ? process.env.ComSpec ?? 'cmd.exe' : './gradlew', windows
    ? ['/d', '/s', '/c', 'gradlew.bat :gkd-a11y-fixture:assembleDebug --console=plain']
    : [':gkd-a11y-fixture:assembleDebug', '--console=plain'], {
    cwd: repositoryRoot, windowsHide: true, timeout: 180_000,
  });
  const folder = join(repositoryRoot, 'gkd-a11y-fixture/build/outputs/apk/debug');
  const apks = (await readdir(folder)).filter(name => name.endsWith('.apk'));
  if (apks.length !== 1) throw new Error(`Expected one fixture APK in ${folder}`);
  return join(folder, apks[0]!);
}
