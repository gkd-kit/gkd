import { execFile, spawn } from 'node:child_process';
import { join } from 'node:path';
import { mkdir, mkdtemp } from 'node:fs/promises';
import { promisify } from 'node:util';
import { repositoryRoot } from '../lib/local-paths.ts';
import { createAdb } from '../lib/device-adb.ts';
import { withAutomationPaused } from '../lib/device-automation.ts';
import { withInstallConfirmation } from '../lib/device-install.ts';
import { installGkdDebug } from '../lib/device-test-apps.ts';

const exec = promisify(execFile);

/** Parse adb's external device-list protocol, including inaccessible devices. */
export function installDevices(output: string, serial?: string) {
  const devices = output.split(/\r?\n/).flatMap(line => {
    const match = line.trim().match(/^(\S+)\s+(device|offline|unauthorized|recovery|sideload|bootloader)(?:\s|$)/);
    return match ? [{ serial: match[1]!, state: match[2]! }] : [];
  });
  if (serial !== undefined && !devices.some(device => device.serial === serial && device.state === 'device')) {
    const state = devices.find(device => device.serial === serial)?.state ?? 'not connected';
    throw new Error(`Device ${serial} is not available: ${state}`);
  }
  const selected = devices.filter(device => device.state === 'device' && (serial === undefined || device.serial === serial));
  if (!selected.length) throw new Error('No connected, authorized Android devices');
  return { selected: selected.map(device => device.serial), skipped: devices.filter(device => device.state !== 'device') };
}

async function build(signal: AbortSignal) {
  signal.throwIfAborted();
  // Use the repository wrapper so JAVA_HOME, JAVA_OPTS and GRADLE_OPTS retain their normal behavior.
  const windows = process.platform === 'win32';
  const child = spawn(windows ? process.env.ComSpec ?? 'cmd.exe' : './gradlew', windows
    ? ['/d', '/s', '/c', 'gradlew.bat :gkd-android:assembleGkdDebug --console=plain']
    : [':gkd-android:assembleGkdDebug', '--console=plain'], {
    cwd: repositoryRoot, windowsHide: true, stdio: 'inherit',
  });
  // On Windows the batch wrapper owns a Java child; stop that owned tree on cancellation.
  let stopping: Promise<void> | undefined;
  const interrupt = () => {
    if (!child.pid) return;
    stopping = windows
      ? exec('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true }).then(() => undefined)
      : Promise.resolve().then(() => { child.kill('SIGTERM'); });
    // Await below; attaching a handler now also covers a fast taskkill failure before close.
    void stopping.catch(() => {});
  };
  signal.addEventListener('abort', interrupt, { once: true });
  try {
    await new Promise<void>((resolve, reject) => {
      child.once('error', reject);
      child.once('close', (code, exitSignal) => code === 0 ? resolve() : reject(new Error(`gkd Debug build failed (${exitSignal ?? code})`)));
      if (signal.aborted) interrupt();
    });
  } finally {
    signal.removeEventListener('abort', interrupt);
    await stopping;
  }
  signal.throwIfAborted();
}

/** Build once, then update selected devices without starting a diagnostic server. */
export async function deviceInstall(serial?: string) {
  const cancellation = new AbortController();
  const interrupt = () => cancellation.abort(new Error('Device installation interrupted; no further devices will be updated'));
  process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
  try {
    const devices = (await createAdb()('devices')).toString();
    cancellation.signal.throwIfAborted();
    const { selected, skipped } = installDevices(devices, serial);
    for (const device of skipped) console.warn(`Skipping ${device.serial}: ${device.state}`);
    console.log(`Target devices: ${selected.join(', ')}`);
    await build(cancellation.signal);
    const failures: Error[] = [];
    for (const device of selected) {
      cancellation.signal.throwIfAborted();
      console.log(`Checking and installing GKD on ${device}`);
      try {
        const parent = join(repositoryRoot, '.temp', 'device-install');
        await mkdir(parent, { recursive: true });
        const folder = await mkdtemp(join(parent, 'update-'));
        const adb = withInstallConfirmation(createAdb(device), cancellation.signal);
        await withAutomationPaused(adb, folder, async () => {
          cancellation.signal.throwIfAborted();
          console.log('GKD APK:', await installGkdDebug(adb));
          cancellation.signal.throwIfAborted();
        });
      } catch (error) {
        const failure = new Error(`Device ${device} failed`, { cause: error });
        if (cancellation.signal.aborted) throw failure;
        failures.push(failure);
        console.error(failure);
      }
    }
    cancellation.signal.throwIfAborted();
    if (failures.length) throw new AggregateError(failures, `${failures.length} device update(s) failed`);
    return { devices: selected };
  } finally {
    process.off('SIGINT', interrupt); process.off('SIGTERM', interrupt);
  }
}
