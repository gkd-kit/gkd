import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile, unlink, rename } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { unzipSync } from 'fflate';
import { localRoot } from '../lib/local-paths.ts';
import { dirname, join } from 'node:path';
import { delay } from '../lib/desktop-client.ts';

type Store = Record<string, unknown>;
export type Adb = (...args: string[]) => Promise<Buffer>;
export interface CaptureOptions {
  serial?: string; apps?: string; font: string; install: boolean; screens: boolean; output: string;
}
const packageName = 'li.songe.gkd.debug';
const remote = `/sdcard/Android/data/${packageName}/files`;
const storePath = `${remote}/store/store.json`;
// Persisted Android settings protocol; must match AutomatorMode.A11y.value.
const a11yAutomatorMode = 1;
const automationKeys = ['enableAutomator', 'automatorMode'] as const;
const mainActivity = `${packageName}/li.gkd.app.MainActivity`;

function command(args: string[], timeout = 45_000, signal?: AbortSignal) {
  const child = spawn('adb', args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'], signal });
  const done = new Promise<Buffer>((resolve, reject) => {
    const stdout: Buffer[] = [], stderr: Buffer[] = [];
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeout);
    child.stdout.on('data', b => stdout.push(Buffer.from(b)));
    child.stderr.on('data', b => stderr.push(Buffer.from(b)));
    child.once('error', error => { clearTimeout(timer); reject(error); });
    child.once('close', code => {
      clearTimeout(timer);
      if (code !== 0 || timedOut) reject(new Error(`adb ${timedOut ? 'timed out' : `exit ${code}`}: ${Buffer.concat(stderr)}${Buffer.concat(stdout)}`));
      else resolve(Buffer.concat(stdout));
    });
  });
  return { child, done };
}
async function readStore(adb: Adb): Promise<Store> {
  return JSON.parse((await adb('shell', 'cat', storePath)).toString()) as Store;
}
async function writeStore(adb: Adb, value: Store) {
  const temporary = join(localRoot, 'tmp', 'capture-profile');
  await mkdir(temporary, { recursive: true });
  const folder = await mkdtemp(join(temporary, 'store-'));
  try {
    const path = join(folder, 'store.json');
    await writeFile(path, JSON.stringify(value));
    await adb('push', path, storePath);
  } finally { await rm(folder, { recursive: true, force: true }); }
}

/** Only the two temporarily changed fields are restored into the latest device store. */
export async function withAutomationPaused<T>(adb: Adb, output: string, work: (original: Store) => Promise<T>): Promise<T> {
  const original = await readStore(adb);
  const saved = Object.fromEntries(automationKeys.filter(k => Object.hasOwn(original, k)).map(k => [k, original[k]]));
  const recovery = join(output, 'automation-recovery.json');
  // Exclusive creation prevents overwriting an unfinished previous recovery record.
  await writeFile(recovery, JSON.stringify(saved), { flag: 'wx' });
  let workError: unknown;
  try {
    await adb('shell', 'am', 'force-stop', packageName);
    await writeStore(adb, { ...await readStore(adb), enableAutomator: false, automatorMode: a11yAutomatorMode });
    const paused = await readStore(adb);
    if (paused.enableAutomator !== false || paused.automatorMode !== a11yAutomatorMode) throw new Error('Automation preflight did not take effect');
    return await work(original);
  } catch (error) { workError = error; throw error; }
  finally {
    try {
      await adb('shell', 'am', 'force-stop', packageName);
      const latest = await readStore(adb);
      for (const key of automationKeys) {
        if (Object.hasOwn(saved, key)) latest[key] = saved[key]; else delete latest[key];
      }
      await writeStore(adb, latest);
      const restored = await readStore(adb);
      for (const key of automationKeys) {
        if (Object.hasOwn(saved, key) !== Object.hasOwn(restored, key) || saved[key] !== restored[key]) throw new Error(`Restore mismatch: ${key}`);
      }
      await adb('shell', 'am', 'start', '-n', mainActivity);
      await unlink(recovery);
      console.log('Restored and verified original automation switch and mode.');
    } catch (error) {
      throw new AggregateError([...(workError === undefined ? [] : [workError]), error], `Automation recovery required: ${recovery}`);
    }
  }
}

export async function captureProfile(options: CaptureOptions, repo: string) {
  await mkdir(options.output, { recursive: true });
  const staging = await mkdtemp(join(dirname(options.output), '.profile-staging-'));
  const prefix = options.serial ? ['-s', options.serial] : [];
  // Cleanup deliberately uses an uncancelled transport.
  const adb: Adb = (...args) => command([...prefix, ...args]).done;
  const cancellation = new AbortController();
  const interrupt = () => cancellation.abort(new Error('Capture interrupted'));
  process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
  const check = () => cancellation.signal.throwIfAborted();
  const pause = (ms: number) => delay(ms, undefined, { signal: cancellation.signal });
  try {
    await withAutomationPaused(adb, options.output, async () => {
      check();
      if (options.install) {
        const folder = join(repo, 'gkd-android/build/outputs/apk/gkd/debug');
        const apks = (await readdir(folder)).filter(n => n.endsWith('.apk'));
        if (apks.length !== 1) throw new Error(`Build gkdDebug first: expected one APK in ${folder}`);
        await adb('install', '-r', '-t', join(folder, apks[0]!)); check();
      }
      const current = await readStore(adb);
      if (current.enableAutomator !== false || current.automatorMode !== a11yAutomatorMode) throw new Error('Automation must remain disabled before export');
      const id = randomUUID();
      const request = `${remote}/development/desktop-profile/${id}`;
      await adb('shell', 'am', 'start', '-n', mainActivity, '-a', 'li.gkd.action.EXPORT_DESKTOP_PROFILE', '--es', 'requestId', id);
      let complete = false;
      for (let i = 0; i < 300; i++) {
        check();
        const data = (await adb('shell', `cat '${request}/status.json' 2>/dev/null || true`)).toString().trim();
        if (data) {
          const status = JSON.parse(data);
          if (status.requestId !== id) throw new Error('Export request ID mismatch');
          if (status.state === 'failed') throw new Error(`Device export failed: ${status.error}`);
          if (status.state === 'success') { complete = true; break; }
        }
        await pause(500);
      }
      if (!complete) throw new Error(`Device export timed out: ${id}`);
      const archive = join(staging, 'profile.zip');
      await adb('pull', `${request}/profile.zip`, archive);
      await extractProfile(await readFile(archive), staging);
      await unlink(archive);
      check();
      await adb('pull', options.font, join(staging, 'DeviceSans.ttf'));
      if (options.apps) {
        const inventory = JSON.parse((await readFile(options.apps, 'utf8')).replace(/^\uFEFF/, ''));
        if (!inventory || !Array.isArray(inventory.apps)) throw new Error('apps.json must contain an apps array');
        await writeFile(join(staging, 'apps.json'), JSON.stringify(inventory));
      }
      check();
      if (options.screens) {
        await adb('shell', 'am', 'force-stop', packageName); await adb('shell', 'am', 'start', '-n', mainActivity); await pause(2000);
        const policy = (await adb('shell', 'dumpsys', 'window', 'policy')).toString();
        if (policy.includes('showing=true') || policy.includes('mIsShowing=true')) throw new Error('Unlock the device before taking screenshots');
        for (const [tab, page] of ['dashboard', 'subscriptions', 'apps', 'settings'].entries()) {
          check();
          await adb('shell', 'am', 'start', '-n', `${packageName}/li.gkd.app.entry.OpenSchemeActivity`, '-a', 'android.intent.action.VIEW', '-d', `gkd://page?tab=${tab}`);
          await pause(1000);
          const focus = (await adb('shell', 'dumpsys', 'window')).toString();
          if (!focus.split('\n').some(line => line.includes('mCurrentFocus=') && line.includes(packageName))) throw new Error('GKD is not the foreground window');
          await writeFile(join(staging, `android-${page}.png`), await adb('exec-out', 'screencap', '-p'));
        }
      }

    });
    check();
    await publishProfile(staging, options.output);
    console.log(`Desktop profile: ${options.output}`);
  } finally {
    process.off('SIGINT', interrupt); process.off('SIGTERM', interrupt);
    await rm(staging, { recursive: true, force: true });
  }
}

/** Validate the complete bundle before writing anything into the staged directory. */
export async function extractProfile(archive: Uint8Array, directory: string) {
  let total = 0;
  const entries = unzipSync(archive, { filter(entry) {
    total += entry.originalSize;
    if (total > 256 * 1024 * 1024) throw new Error('Profile exceeds 256 MiB');
    if (!/^(profile|apps|settings|seed)\.json$|^subscriptions\/-?\d+\.json$|^icons\/[a-zA-Z0-9_.]+\.png$/.test(entry.name)) {
      throw new Error(`Unexpected profile entry: ${entry.name}`);
    }
    return true;
  } });
  const json = (name: string) => {
    const bytes = entries[name];
    if (!bytes) throw new Error(`Missing ${name}`);
    const value = JSON.parse(Buffer.from(bytes).toString('utf8'));
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`Invalid ${name}`);
    return value;
  };
  const profile = json('profile.json');
  if (!(profile.width > 0 && profile.height > 0 && profile.fontScale > 0)) throw new Error('Invalid window dimensions');
  if (!Array.isArray(json('apps.json').apps)) throw new Error('Invalid app inventory');
  json('settings.json');
  const seed = json('seed.json');
  if (!Array.isArray(seed.blocked) || !seed.blocked.every((id: unknown) => typeof id === 'string') ||
      !seed.subscriptions || typeof seed.subscriptions !== 'object' || Array.isArray(seed.subscriptions) ||
      !Object.entries(seed.subscriptions).every(([id, enabled]) => /^-?\d+$/.test(id) && typeof enabled === 'boolean')) {
    throw new Error('Invalid profile seed');
  }
  for (const name of Object.keys(entries).filter(n => n.startsWith('subscriptions/'))) {
    const sub = json(name);
    if (`subscriptions/${sub.id}.json` !== name || typeof sub.name !== 'string' || !Number.isInteger(sub.version)) throw new Error(`Invalid subscription: ${name}`);
  }
  for (const [name, bytes] of Object.entries(entries)) {
    const destination = join(directory, name);
    await mkdir(dirname(destination), { recursive: true });
    await writeFile(destination, bytes);
  }
}

/** A failed export never overwrites the current profile or its automation recovery record. */
export async function publishProfile(staging: string, output: string) {
  const previous = `${staging}-previous`;
  await rename(output, previous);
  try { await rename(staging, output); }
  catch (error) { await rename(previous, output); throw error; }
  await rm(previous, { recursive: true, force: true });
}
