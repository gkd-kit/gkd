import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { test } from 'node:test';
import type { Adb } from './device-automation.ts';
import { cleanupDeviceTestApps, deviceTestAppsStatus, ensureDeviceTestApp, installGkdDebug, installedApkHash } from './device-test-apps.ts';
import { assertDesktopTestPath, desktopTests } from './local-paths.ts';
import { withInstallConfirmation } from './device-install.ts';

const fixture = 'li.gkd.a11y.fixture';
const hash = (bytes: Uint8Array) => createHash('sha256').update(bytes).digest('hex');

async function withFixture(work: (context: Awaited<ReturnType<typeof createFixture>>) => Promise<void>) {
  const context = await createFixture();
  try { await work(context); }
  finally {
    assertDesktopTestPath(context.directory);
    await rm(context.directory, { recursive: true, force: true });
  }
}

async function createFixture() {
  await mkdir(desktopTests, { recursive: true });
  const directory = await mkdtemp(join(desktopTests, 'device-test-apps-'));
  const ledger = join(directory, 'round');
  const apk = join(directory, 'fixture.apk');
  await writeFile(apk, 'first APK');
  const device = {
    packages: new Map<string, string>(),
    calls: [] as string[][],
    installFailure: undefined as 'before' | 'after' | undefined,
    uninstallFailure: undefined as 'before' | 'after' | undefined,
    readFailure: false,
    installPackage: fixture,
  };
  const reconnect = (): Adb => async (...args) => {
    device.calls.push(args);
    if (args[0] === 'get-serialno') return Buffer.from('fixture-test-device');
    if (args[0] === 'install') {
      assert.deepEqual(args.slice(0, 5), ['install', '-r', '-t', '--pkg', device.installPackage],
        'Updates preserve data and explicitly bind the expected package');
      const expected = hash(await readFile(args[5]!));
      if (device.installPackage === fixture) {
        const record = (await deviceTestAppsStatus(reconnect(), ledger)).record;
        assert.ok(record?.apps.find(app => app.packageName === fixture)?.hashes.includes(expected),
          'Persist installation ownership before an adb command can lose its response');
      }
      if (device.installFailure === 'before') throw new Error('Installation rejected');
      device.packages.set(device.installPackage, expected);
      if (device.installFailure === 'after') throw new Error('Lost install response');
      return Buffer.from('Success');
    }
    if (args[0] === 'uninstall') {
      if (device.uninstallFailure === 'before') throw new Error('Device disconnected before uninstall');
      device.packages.delete(args[1]!);
      if (device.uninstallFailure === 'after') throw new Error('Lost uninstall response');
      return Buffer.from('Success');
    }
    if (args[0] === 'shell' && args[1] === 'pm' && args[2] === 'list') {
      if (device.readFailure) throw new Error('Device offline');
      return Buffer.from([...device.packages.keys()].filter(name => name.includes(args[4]!)).map(name => `package:${name}`).join('\n'));
    }
    if (args[0] === 'shell' && args[1] === 'pm' && args[2] === 'path') {
      assert.ok(device.packages.has(args[3]!));
      return Buffer.from(`package:/data/app/${args[3]}/base.apk\n`);
    }
    if (args[0] === 'shell' && args[1] === 'sha256sum') {
      const packageName = args[2]!.split('/')[3]!;
      return Buffer.from(`${device.packages.get(packageName)}  ${args[2]}\n`);
    }
    throw new Error(`Unexpected adb call: ${JSON.stringify(args)}`);
  };
  return { directory, ledger, apk, device, reconnect };
}

test('a round retains installation across calls, skips identical APKs, updates changed APKs and cleans once', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    assert.equal((await ensureDeviceTestApp(reconnect(), fixture, apk, ledger)).action, 'installed');
    const firstHash = device.packages.get(fixture);
    // Re-read the durable ledger with a new adb connection, as a subsequent CLI invocation does.
    assert.equal((await deviceTestAppsStatus(reconnect(), ledger)).record?.apps[0]?.hashes[0], firstHash);
    assert.equal((await ensureDeviceTestApp(withInstallConfirmation(reconnect()), fixture, apk, ledger)).action, 'unchanged');
    assert.equal(device.calls.filter(args => args[0] === 'install').length, 1);
    assert.equal(device.calls.filter(args => args[0] === 'uninstall').length, 0);
    assert.equal(device.calls.some(args => args[1] === 'dumpsys' || args[1] === 'uiautomator' || args[1] === 'input'), false);

    await writeFile(apk, 'changed APK');
    assert.equal((await ensureDeviceTestApp(reconnect(), fixture, apk, ledger)).action, 'updated');
    assert.notEqual(device.packages.get(fixture), firstHash);
    assert.equal(device.calls.filter(args => args[0] === 'install').length, 2);
    assert.equal(device.calls.filter(args => args[0] === 'uninstall').length, 0);
    device.packages.set('unrelated.app', hash(Buffer.from('unrelated')));

    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, [fixture]);
    assert.equal((await deviceTestAppsStatus(reconnect(), ledger)).record, null);
    assert.equal(device.packages.has('unrelated.app'), true);
    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, []);
    assert.equal(device.calls.filter(args => args[0] === 'uninstall').length, 1);
  });
});

test('unowned same-package APKs and externally replaced APKs are never updated or removed', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    device.packages.set(fixture, hash(await readFile(apk)));
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /not owned/);
    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, []);
    assert.equal(device.calls.some(args => args[0] === 'install' || args[0] === 'uninstall'), false);

    device.packages.delete(fixture);
    await ensureDeviceTestApp(reconnect(), fixture, apk, ledger);
    device.packages.set(fixture, hash(Buffer.from('externally replaced')));
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /not owned/);
    await assert.rejects(cleanupDeviceTestApps(reconnect(), ledger), /changed outside/);
    assert.equal(device.calls.filter(args => args[0] === 'install').length, 1);
    assert.equal(device.calls.some(args => args[0] === 'uninstall'), false);
    assert.ok((await deviceTestAppsStatus(reconnect(), ledger)).record);
  });
});

test('GKD build discovery, update and identical-APK skip never start diagnostic transport or make the app eligible for cleanup', async () => {
  await withFixture(async ({ directory, ledger, apk, device, reconnect }) => {
    const gkd = 'li.songe.gkd.debug';
    const output = join(directory, 'gkd-android/build/outputs/apk/gkd/debug');
    await mkdir(output, { recursive: true });
    await writeFile(join(output, 'gkd.apk'), await readFile(apk));
    device.installPackage = gkd;
    device.packages.set(gkd, hash(Buffer.from('previous GKD build')));
    // The fake adb rejects all HTTP forwarding/service commands: installation must stand alone.
    assert.equal((await installGkdDebug(reconnect(), directory)).action, 'updated');
    assert.equal((await installGkdDebug(reconnect(), directory)).action, 'unchanged');
    assert.equal(device.calls.filter(args => args[0] === 'install').length, 1);
    await writeFile(join(output, 'ambiguous.apk'), 'another build');
    const calls = device.calls.length;
    await assert.rejects(installGkdDebug(reconnect(), directory), /expected one APK/);
    assert.equal(device.calls.length, calls, 'Ambiguous builds must not touch the device');
    assert.equal((await deviceTestAppsStatus(reconnect(), ledger)).record, null);
    device.installPackage = fixture;
    await ensureDeviceTestApp(reconnect(), fixture, apk, ledger);
    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, [fixture]);
    assert.equal(device.packages.get(gkd), hash(await readFile(apk)));
    assert.equal(device.calls.some(args => args[0] === 'uninstall' && args[1] === gkd), false);
  });
});

test('rejected install can retry and a lost successful install response does not trigger another install', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    device.installFailure = 'before';
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /Installation rejected/);
    assert.ok((await deviceTestAppsStatus(reconnect(), ledger)).record);
    assert.equal(device.packages.has(fixture), false);
    device.installFailure = 'after';
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /Lost install response/);
    assert.equal(device.packages.has(fixture), true);
    device.installFailure = undefined;
    assert.equal((await ensureDeviceTestApp(reconnect(), fixture, apk, ledger)).action, 'unchanged');
    assert.equal(device.calls.filter(args => args[0] === 'install').length, 2);
    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, [fixture]);
  });
});

test('ending a round after a rejected install clears its ledger without uninstalling a nonexistent app', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    device.installFailure = 'before';
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /Installation rejected/);
    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, []);
    assert.equal((await deviceTestAppsStatus(reconnect(), ledger)).record, null);
    assert.equal(device.calls.some(args => args[0] === 'uninstall'), false);
  });
});

test('cleanup remains retryable before and after a lost uninstall response', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    await ensureDeviceTestApp(reconnect(), fixture, apk, ledger);
    device.uninstallFailure = 'before';
    await assert.rejects(cleanupDeviceTestApps(reconnect(), ledger), /disconnected before/);
    assert.ok((await deviceTestAppsStatus(reconnect(), ledger)).record);
    assert.equal(device.packages.has(fixture), true);
    device.uninstallFailure = 'after';
    await assert.rejects(cleanupDeviceTestApps(reconnect(), ledger), /Lost uninstall response/);
    assert.ok((await deviceTestAppsStatus(reconnect(), ledger)).record);
    assert.equal(device.packages.has(fixture), false);
    device.uninstallFailure = undefined;
    assert.deepEqual((await cleanupDeviceTestApps(reconnect(), ledger)).removed, []);
    assert.equal((await deviceTestAppsStatus(reconnect(), ledger)).record, null);
    assert.equal(device.calls.filter(args => args[0] === 'uninstall').length, 2);
  });
});

test('adb read failure is not treated as absence and package lookup requires an exact package name', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    device.packages.set(`${fixture}.other`, hash(Buffer.from('different package')));
    assert.equal(await installedApkHash(reconnect(), fixture), null);
    await ensureDeviceTestApp(reconnect(), fixture, apk, ledger);
    const status = await deviceTestAppsStatus(reconnect(), ledger);
    const saved = await readFile(status.path, 'utf8');
    device.readFailure = true;
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /Device offline/);
    await assert.rejects(cleanupDeviceTestApps(reconnect(), ledger), /Device offline/);
    assert.equal(await readFile(status.path, 'utf8'), saved);
    assert.equal(device.calls.filter(args => args[0] === 'install').length, 1);
    assert.equal(device.calls.some(args => args[0] === 'uninstall'), false);
  });
});

test('a ledger copied from another device is rejected before device mutations', async () => {
  await withFixture(async ({ ledger, apk, device, reconnect }) => {
    await ensureDeviceTestApp(reconnect(), fixture, apk, ledger);
    const status = await deviceTestAppsStatus(reconnect(), ledger);
    await writeFile(status.path, JSON.stringify({ ...status.record, serial: 'different-device' }));
    const callsBefore = device.calls.length;
    await assert.rejects(ensureDeviceTestApp(reconnect(), fixture, apk, ledger), /identity mismatch/);
    await assert.rejects(cleanupDeviceTestApps(reconnect(), ledger), /identity mismatch/);
    assert.equal(device.calls.slice(callsBefore).some(args => args[0] === 'install' || args[0] === 'uninstall'), false);
    assert.equal(device.packages.has(fixture), true);
  });
});
