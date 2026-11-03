import { mkdir, mkdtemp, readFile, writeFile, rm, unlink } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { join, resolve } from 'node:path';
import { localRoot, repositoryRoot } from './local-paths.ts';

type Store = Record<string, unknown>;
export type Adb = (...args: string[]) => Promise<Buffer>;
export function createDeviceAutomation(packageName: 'li.songe.gkd.debug' | 'li.songe.gkd') {
  const remote = `/sdcard/Android/data/${packageName}/files`;
  const storePath = `${remote}/store/store.json`;
  const keys = ['enableAutomator', 'automatorMode'] as const;
  const accessibilityKeys = [...keys, 'enableMatch', 'enableBlockA11yAppList'] as const;
  const standard = `${packageName}/com.google.android.accessibility.selecttospeak.SelectToSpeakService`;
  const privileged = standard + 'Privileged';
  const companion = standard + 'Companion';
  const ownedComponents = new Set([standard, privileged, companion]);
  const recoveryRoot = packageName === 'li.songe.gkd.debug' ? join(localRoot, 'device-recovery') : join(localRoot, 'device-recovery', 'release');
  const transferRoot = join(repositoryRoot, '.temp', 'device-automation');
  type Recovery = { serial: string; packageName: string; captureDirectory?: string; saved: Store;
    diagnostics?: boolean; accessibility?: { services: string; enabled: string } };
  const serviceList = (value: string) => value === 'null' || value === '' ? [] : value.split(':');
  const readSecure = async (adb: Adb, key: string) => (await adb('shell', 'settings', 'get', 'secure', key)).toString().trim();
  const writeSecure = async (adb: Adb, key: string, value: string) => value === 'null'
    ? adb('shell', 'settings', 'delete', 'secure', key) : adb('shell', 'settings', 'put', 'secure', key, value);
  const normalize = (component: string) => {
    const [pkg, name] = component.split('/');
    return name?.startsWith('.') ? `${pkg}/${pkg}${name}` : component;
  };
  const owned = (component: string) => ownedComponents.has(normalize(component));
  const componentKey = (list: string[]) => list.map(normalize).sort().join(':');

  /** Explicit device diagnostic preparation; create the recovery record before enabling the service. */
  async function enableDebugAccessibility(adb: Adb, component: string) {
    if ((await readStore(adb)).enableMatch !== false) throw new Error('Disable rule matching before enabling accessibility');
    if (component !== standard && component !== privileged) throw new Error('Unexpected diagnostic accessibility component');
    const current = serviceList(await readSecure(adb, 'enabled_accessibility_services'));
    const selected = current.filter(item => {
      const name = normalize(item);
      return name !== standard && name !== privileged;
    });
    selected.push(component);
    if (componentKey(current) !== componentKey(selected)) {
      await writeSecure(adb, 'enabled_accessibility_services', selected.join(':'));
    }
    await writeSecure(adb, 'accessibility_enabled', '1');
    if (!serviceList(await readSecure(adb, 'enabled_accessibility_services')).map(normalize).includes(component)) {
      throw new Error('Diagnostic accessibility service was not enabled');
    }
  }

  async function restoreAccessibility(adb: Adb, saved: NonNullable<Recovery['accessibility']>) {
    const original = serviceList(saved.services);
    const current = serviceList(await readSecure(adb, 'enabled_accessibility_services'));
    const originalOwn = original.filter(owned);
    const currentOthers = current.filter(item => !owned(item));
    if (componentKey(current.filter(owned)) !== componentKey(originalOwn)) {
      const restored = [...currentOthers, ...originalOwn];
      await writeSecure(adb, 'enabled_accessibility_services', restored.length ? restored.join(':') : saved.services === 'null' ? 'null' : '');
    }
    // Do not disable another service added by the user during capture.
    const restoreGlobalFlag = componentKey(original.filter(item => !owned(item))) === componentKey(currentOthers);
    if (restoreGlobalFlag) await writeSecure(adb, 'accessibility_enabled', saved.enabled);
    const actual = serviceList(await readSecure(adb, 'enabled_accessibility_services'));
    if (componentKey(actual.filter(owned)) !== componentKey(originalOwn)) {
      throw new Error('Accessibility restoration mismatch');
    }
    if (restoreGlobalFlag && await readSecure(adb, 'accessibility_enabled') !== saved.enabled) {
      throw new Error('Accessibility enabled setting restoration mismatch');
    }
  }

  async function readStore(adb: Adb): Promise<Store> {
    // Missing settings uses production defaults only when the store directory exists.
    return JSON.parse((await adb('shell',
      `if [ -f '${storePath}' ]; then cat '${storePath}'; elif [ -d '${remote}/store' ]; then printf '{}'; else exit 1; fi`
    )).toString()) as Store;
  }

  async function writeStore(adb: Adb, value: Store) {
    await mkdir(transferRoot, { recursive: true });
    const folder = await mkdtemp(join(transferRoot, 'store-'));
    try {
      const path = join(folder, 'store.json');
      await writeFile(path, JSON.stringify(value));
      // An interrupted push must not truncate the current settings file.
      const pending = `${storePath}.automation-pending`;
      await adb('push', path, pending);
      await adb('shell', 'mv', '-f', pending, storePath);
    } finally { await rm(folder, { recursive: true, force: true }); }
  }

  async function recoveryLocation(adb: Adb, directory: string) {
    const serial = (await adb('get-serialno')).toString().trim();
    if (!serial || serial === 'unknown') throw new Error('A connected, identified device is required');
    return { serial, path: join(directory, `${createHash('sha256').update(serial).digest('hex')}.json`) };
  }

  function validateRecovery(value: Recovery, serial: string) {
    if (!value || value.serial !== serial || value.packageName !== packageName ||
        !value.saved || typeof value.saved !== 'object' || Array.isArray(value.saved) ||
        Object.keys(value.saved).some(key => !accessibilityKeys.includes(key as typeof accessibilityKeys[number])) ||
        (value.diagnostics !== undefined && typeof value.diagnostics !== 'boolean') ||
        (Object.hasOwn(value.saved, 'enableAutomator') && typeof value.saved.enableAutomator !== 'boolean') ||
        (Object.hasOwn(value.saved, 'enableMatch') && typeof value.saved.enableMatch !== 'boolean') ||
        (Object.hasOwn(value.saved, 'enableBlockA11yAppList') && typeof value.saved.enableBlockA11yAppList !== 'boolean') ||
        (value.accessibility !== undefined && (!value.accessibility || typeof value.accessibility.services !== 'string' ||
          !['null', '0', '1'].includes(value.accessibility.enabled))) ||
        (Object.hasOwn(value.saved, 'automatorMode') && ![1, 2].includes(value.saved.automatorMode as number))) {
      throw new Error('Invalid automation recovery record or device identity mismatch');
    }
  }

  async function automationRecoveryStatus(adb: Adb, directory = recoveryRoot) {
    const location = await recoveryLocation(adb, directory);
    try {
      const record: Recovery = JSON.parse(await readFile(location.path, 'utf8'));
      validateRecovery(record, location.serial);
      return { ...location, record };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return { ...location, record: null };
      throw error;
    }
  }

  /** Retryable after reconnect: merge only the saved fields into the latest stopped-app store. */
  async function restoreAutomation(adb: Adb, directory = recoveryRoot, reopenApp = true) {
    const { path, record } = await automationRecoveryStatus(adb, directory);
    if (!record) return { restored: false, path };
    await adb('shell', 'am', 'force-stop', packageName);
    const latest = await readStore(adb);
    const restoredKeys = (record.diagnostics ?? Boolean(record.accessibility)) ? accessibilityKeys : keys;
    for (const key of restoredKeys) {
      if (Object.hasOwn(record.saved, key)) latest[key] = record.saved[key]; else delete latest[key];
    }
    await writeStore(adb, latest);
    const restored = await readStore(adb);
    for (const key of restoredKeys) {
      if (Object.hasOwn(record.saved, key) !== Object.hasOwn(restored, key) || record.saved[key] !== restored[key]) {
        throw new Error(`Restore mismatch: ${key}; recovery retained at ${path}`);
      }
    }
    if (record.accessibility) await restoreAccessibility(adb, record.accessibility);
    if (reopenApp) await adb('shell', 'am', 'start', '-n', `${packageName}/li.gkd.app.MainActivity`);
    await unlink(path);
    return { restored: true, path };
  }

  async function withAutomationPaused<T>(adb: Adb, output: string, work: (original: Store) => Promise<T>, directory = recoveryRoot,
    options: { accessibilityForDiagnostics?: boolean; automationForDiagnostics?: boolean; reopenApp?: boolean } = {}): Promise<T> {
    const { serial, path, record } = await automationRecoveryStatus(adb, directory);
    if (record) throw new Error(`Automation recovery required before a new capture: ${path}. Run device-recovery --serial ${serial} --operation restore${packageName === 'li.songe.gkd' ? ' --release' : ''}`);
    const original = await readStore(adb);
    const changedKeys = options.accessibilityForDiagnostics ? accessibilityKeys : keys;
    const saved = Object.fromEntries(changedKeys.filter(key => Object.hasOwn(original, key)).map(key => [key, original[key]]));
    const recovery: Recovery = {
      serial, packageName, captureDirectory: resolve(output), saved, diagnostics: options.accessibilityForDiagnostics === true,
      accessibility: {
        services: await readSecure(adb, 'enabled_accessibility_services'), enabled: await readSecure(adb, 'accessibility_enabled'),
      },
    };
    if (options.automationForDiagnostics && !options.accessibilityForDiagnostics) throw new Error('Automation testing requires diagnostic recovery');
    validateRecovery(recovery, serial);
    await mkdir(directory, { recursive: true });
    // One record per device prevents another capture directory overwriting the original settings.
    await writeFile(path, JSON.stringify(recovery), { flag: 'wx', flush: true });
    let workError: unknown;
    try {
      await adb('shell', 'am', 'force-stop', packageName);
      await writeStore(adb, { ...await readStore(adb), enableAutomator: false, automatorMode: 1,
        ...(options.accessibilityForDiagnostics ? { enableMatch: false, enableBlockA11yAppList: false } : {}) });
      const paused = await readStore(adb);
      if (paused.enableAutomator !== false || paused.automatorMode !== 1) throw new Error('Automation preflight did not take effect');
      if (options.accessibilityForDiagnostics && paused.enableMatch !== false) throw new Error('Matching preflight did not take effect');
      return await work(original);
    } catch (error) { workError = error; throw error; }
    finally {
      try {
        await restoreAutomation(adb, directory, options.reopenApp);
        console.log(options.accessibilityForDiagnostics
          ? 'Restored and verified automation, rule matching and accessibility settings.'
          : 'Restored and verified original automation switch, mode and accessibility settings.');
      } catch (error) {
        throw new AggregateError([...(workError === undefined ? [] : [workError]), error], `Automation recovery required: ${path}`);
      }
    }
  }

  return { enableDebugAccessibility, readStore, automationRecoveryStatus, restoreAutomation, withAutomationPaused };
}

export const { enableDebugAccessibility, readStore, automationRecoveryStatus, restoreAutomation, withAutomationPaused } = createDeviceAutomation('li.songe.gkd.debug');
