import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, rename, unlink, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { localRoot, repositoryRoot } from './local-paths.ts';
import type { Adb } from './device-automation.ts';

const rounds = join(localRoot, 'device-test-apps');
type TestApp = { packageName: string; hashes: string[] };
type Round = { serial: string; apps: TestApp[] };
const digest = (content: Uint8Array) => createHash('sha256').update(content).digest('hex');

export async function deviceTestAppsStatus(adb: Adb, directory = rounds) {
  const serial = (await adb('get-serialno')).toString().trim();
  if (!serial || serial === 'unknown') throw new Error('A connected, identified device is required');
  const path = join(directory, `${digest(Buffer.from(serial))}.json`);
  let record: Round;
  try { record = JSON.parse(await readFile(path, 'utf8')); }
  catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return { serial, path, record: null };
    throw error;
  }
  if (record?.serial !== serial || !Array.isArray(record.apps) || record.apps.some(app =>
    !/^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$/.test(app.packageName) || !Array.isArray(app.hashes) ||
    !app.hashes.length || app.hashes.some(hash => !/^[a-f0-9]{64}$/.test(hash))) ||
    new Set(record.apps.map(app => app.packageName)).size !== record.apps.length) {
    throw new Error('Invalid test app round or device identity mismatch');
  }
  return { serial, path, record };
}

async function saveRound(path: string, record: Round) {
  await writeFile(`${path}.pending`, JSON.stringify(record, null, 2), { flush: true });
  await rename(`${path}.pending`, path);
}

export async function installedApkHash(adb: Adb, packageName: string) {
  const packages = (await adb('shell', 'pm', 'list', 'packages', packageName)).toString().split(/\r?\n/);
  if (!packages.includes(`package:${packageName}`)) return null;
  const paths = (await adb('shell', 'pm', 'path', packageName)).toString().trim().split(/\r?\n/);
  if (paths.length !== 1 || !paths[0]?.startsWith('package:')) throw new Error(`Expected a single APK for ${packageName}`);
  const hash = (await adb('shell', 'sha256sum', paths[0].slice('package:'.length))).toString().split(/\s+/)[0]!;
  if (!/^[a-f0-9]{64}$/.test(hash)) throw new Error(`Cannot read installed APK hash for ${packageName}`);
  return hash;
}

async function installApk(adb: Adb, packageName: string, apk: string, sha256: string, previous: string | null) {
  await adb('install', '-r', '-t', '--pkg', packageName, apk);
  if (await installedApkHash(adb, packageName) !== sha256) throw new Error(`Installed APK verification failed: ${packageName}`);
  return { packageName, sha256, action: previous ? 'updated' as const : 'installed' as const };
}

/** GKD itself can be updated without becoming a temporary app owned by the test round. */
async function installApkIfChanged(adb: Adb, packageName: string, apk: string) {
  const sha256 = digest(await readFile(apk));
  const previous = await installedApkHash(adb, packageName);
  if (previous === sha256) return { packageName, sha256, action: 'unchanged' as const };
  return installApk(adb, packageName, apk, sha256, previous);
}

/** Called inside the owner's automation pause/restore boundary; GKD is never a temporary test app. */
export async function installGkdDebug(adb: Adb, repo = repositoryRoot) {
  const folder = join(repo, 'gkd-android/build/outputs/apk/gkd/debug');
  const apks = (await readdir(folder)).filter(name => name.endsWith('.apk'));
  if (apks.length !== 1) throw new Error(`Build gkdDebug first: expected one APK in ${folder}`);
  return installApkIfChanged(adb, 'li.songe.gkd.debug', join(folder, apks[0]!));
}

/** Called inside the device's existing automation pause/restore boundary, which excludes concurrent captures. */
export async function ensureDeviceTestApp(adb: Adb, packageName: string, apk: string, directory = rounds) {
  if (!/^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$/.test(packageName)) throw new Error('Invalid test app package');
  const status = await deviceTestAppsStatus(adb, directory);
  const record: Round = status.record ?? { serial: status.serial, apps: [] };
  const owned = record.apps.find(app => app.packageName === packageName);
  const installedHash = await installedApkHash(adb, packageName);
  if (installedHash && (!owned || !owned.hashes.includes(installedHash))) {
    throw new Error(`Refuse to replace ${packageName}: installed APK is not owned by this test round`);
  }
  const sha256 = digest(await readFile(apk));
  if (installedHash === sha256) return { packageName, sha256, action: 'unchanged' as const };
  // Preserve ownership before install: an interrupted adb response may still leave an installed APK.
  const app = owned ?? { packageName, hashes: [] };
  if (!owned) record.apps.push(app);
  if (!app.hashes.includes(sha256)) app.hashes.push(sha256);
  await mkdir(directory, { recursive: true });
  await saveRound(status.path, record);
  return installApk(adb, packageName, apk, sha256, installedHash);
}

/** End a whole round, not an individual check. Failed cleanup remains retryable after reconnect. */
export async function cleanupDeviceTestApps(adb: Adb, directory = rounds) {
  const { path, record } = await deviceTestAppsStatus(adb, directory);
  const removed: string[] = [];
  if (!record) return { removed, path };
  for (const app of [...record.apps]) {
    const installedHash = await installedApkHash(adb, app.packageName);
    if (installedHash && !app.hashes.includes(installedHash)) {
      throw new Error(`Refuse to uninstall ${app.packageName}: installed APK changed outside this test round`);
    }
    if (installedHash) {
      await adb('uninstall', app.packageName);
      if (await installedApkHash(adb, app.packageName)) throw new Error(`Test app uninstall failed: ${app.packageName}`);
      removed.push(app.packageName);
    }
    record.apps = record.apps.filter(item => item.packageName !== app.packageName);
    await saveRound(path, record);
  }
  await unlink(path);
  return { removed, path };
}
