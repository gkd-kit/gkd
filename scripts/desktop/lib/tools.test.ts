import { assertDesktopTestPath, desktopTests, localRoot } from './local-paths.ts';
import { zipSync, strToU8 } from 'fflate';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, access, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { extractProfile, publishProfile } from '../commands/capture-profile.ts';
import { withAutomationPaused, automationRecoveryStatus, restoreAutomation, enableDebugAccessibility, type Adb } from './device-automation.ts';

test('benchmark recovery survives disconnect and preserves other accessibility services and settings', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-benchmark-test-'));
  let store: Record<string, unknown> = { enableAutomator: false, automatorMode: 2, enableMatch: true };
  let staged: Record<string, unknown> | undefined;
  const secure: Record<string, string> = { enabled_accessibility_services: 'null', accessibility_enabled: '0' };
  let disconnected = false;
  const adb: Adb = async (...args) => {
    if (disconnected) throw new Error('offline');
    if (args[0] === 'get-serialno') return Buffer.from('benchmark-device');
    if (args[0] === 'push') staged = JSON.parse(await readFile(args[1]!, 'utf8'));
    if (args[1] === 'mv') store = staged!;
    if (args[1] === 'settings') {
      if (args[2] === 'get') return Buffer.from(secure[args[4]!] ?? 'null');
      secure[args[4]!] = args[2] === 'delete' ? 'null' : args[5]!;
    }
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(enableDebugAccessibility(adb), /Disable rule matching/);
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.equal(store.enableAutomator, false);
      assert.equal(store.automatorMode, 1);
      assert.equal(store.enableMatch, false);
      await enableDebugAccessibility(adb);
      assert.match(secure.enabled_accessibility_services!, /SelectToSpeakService/);
      // Simulate service connection and independent user edits before losing ADB.
      store.enableAutomator = true;
      store.theme = 'dark';
      secure.enabled_accessibility_services += ':other.app/OtherService';
      disconnected = true;
    }, dir, { accessibilityForDiagnostics: true }), /recovery required/);
    disconnected = false;
    assert.equal((await automationRecoveryStatus(adb, dir)).record?.saved.enableMatch, true);
    await restoreAutomation(adb, dir);
    assert.deepEqual({ ...store }, { enableAutomator: false, automatorMode: 2, enableMatch: true, theme: 'dark' });
    assert.deepEqual(secure, { enabled_accessibility_services: 'other.app/OtherService', accessibility_enabled: '1' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
    // When no unrelated service changes, restore the original global flag and absent setting.
    secure.enabled_accessibility_services = 'null'; secure.accessibility_enabled = '0';
    delete store.enableMatch;
    await withAutomationPaused(adb, dir, async () => { await enableDebugAccessibility(adb); }, dir,
      { accessibilityForDiagnostics: true });
    assert.deepEqual(secure, { enabled_accessibility_services: 'null', accessibility_enabled: '0' });
    assert.equal(Object.hasOwn(store, 'enableMatch'), false);
    // Actual Android 13 normalization must not be mistaken for another service being added.
    secure.enabled_accessibility_services = 'other.app/other.app.Service';
    secure.accessibility_enabled = '0';
    await withAutomationPaused(adb, dir, async () => {
      await enableDebugAccessibility(adb);
      secure.enabled_accessibility_services = secure.enabled_accessibility_services!.replace('other.app/other.app.Service', 'other.app/.Service');
    }, dir, { accessibilityForDiagnostics: true });
    assert.deepEqual(secure, { enabled_accessibility_services: 'other.app/.Service', accessibility_enabled: '0' });
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('automation diagnostics restores test app scope after disconnect without overwriting other scope edits', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-scope-test-'));
  const app = 'li.songe.gkd.debug';
  let store: Record<string, unknown> = { enableAutomator: true, automatorMode: 2, enableMatch: true, enableBlockA11yAppList: true };
  let scope = ['other.app', app];
  let staged = '', disconnected = false;
  const adb: Adb = async (...args) => {
    if (disconnected) throw new Error('offline');
    if (args[0] === 'get-serialno') return Buffer.from('scope-device');
    if (args[0] === 'push') staged = await readFile(args[1]!, 'utf8');
    if (args[1] === 'mv') {
      if (args.at(-1)!.endsWith('.txt')) scope = staged.split('\n').filter(Boolean);
      else store = JSON.parse(staged);
    }
    if (args[1] === 'settings' && args[2] === 'get') return Buffer.from(args[4] === 'accessibility_enabled' ? '0' : 'null');
    if (args[1]?.startsWith('if [ -f')) return Buffer.from(args[1].includes('a11y_scope_app_list') ? scope.join('\n') : JSON.stringify(store));
    return Buffer.from('');
  };
  const options = { accessibilityForDiagnostics: true, automationForDiagnostics: true };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.deepEqual(scope, ['other.app']);
      assert.equal(store.enableMatch, false);
      assert.equal(store.enableBlockA11yAppList, false);
      scope.push('new.app');
      store.theme = 'dark';
      disconnected = true;
    }, dir, options), /recovery required/);
    disconnected = false;
    assert.equal((await automationRecoveryStatus(adb, dir)).record?.restoreTestAppA11yScope, true);
    await restoreAutomation(adb, dir);
    assert.deepEqual(scope, ['other.app', 'new.app', app]);
    assert.deepEqual(store, { enableAutomator: true, automatorMode: 2, enableMatch: true, enableBlockA11yAppList: true, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
    // Absent scope membership and absent settings must remain absent after diagnostics.
    scope = ['other.app'];
    Reflect.deleteProperty(store, 'enableBlockA11yAppList');
    await withAutomationPaused(adb, dir, async () => {
      assert.equal((await automationRecoveryStatus(adb, dir)).record?.restoreTestAppA11yScope, undefined);
      assert.equal(store.enableBlockA11yAppList, false);
    }, dir, options);
    assert.deepEqual(scope, ['other.app']);
    assert.equal(Object.hasOwn(store, 'enableBlockA11yAppList'), false);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('capture restores only automation fields on failure, preserving intervening settings', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-capture-test-'));
  let store: Record<string, unknown> = { enableAutomator: true, automatorMode: 2, theme: 'light' };
  let pending: Record<string, unknown> | undefined;
  const adb: Adb = async (...args) => {
    if (args[0] === 'get-serialno') return Buffer.from('test-device');
    if (args[0] === 'push') pending = JSON.parse(await readFile(args[1]!, 'utf8'));
    if (args[1] === 'mv') store = pending!;
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.equal(store.enableAutomator, false); assert.equal(store.automatorMode, 1);
      store.theme = 'dark'; throw new Error('export failed');
    }, dir), /export failed/);
    assert.deepEqual(store, { enableAutomator: true, automatorMode: 2, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('capture retains recovery record when restoration fails and refuses another run', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-capture-test-'));
  let store: Record<string, unknown> = { enableAutomator: true };
  let staged: Record<string, unknown> | undefined;
  let failRestore = false;
  const adb: Adb = async (...args) => {
    if (args[0] === 'get-serialno') return Buffer.from('test-device');
    if (args[0] === 'push') {
      if (failRestore) throw new Error('device disconnected');
      staged = JSON.parse(await readFile(args[1]!, 'utf8'));
    }
    if (args[1] === 'mv') store = staged!;
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => { failRestore = true; }, dir), /recovery required/);
    const { path, record } = await automationRecoveryStatus(adb, dir);
    assert.deepEqual(record?.saved, { enableAutomator: true });
    // A different capture directory must still find the unfinished record for this device.
    await assert.rejects(withAutomationPaused(adb, join(dir, 'new-capture'), async () => assert.fail('Must not run'), dir), /recovery required/);
    assert.deepEqual(JSON.parse(await readFile(path, 'utf8')), record);
    failRestore = false;
    store.theme = 'changed while disconnected';
    assert.equal((await restoreAutomation(adb, dir)).restored, true);
    assert.deepEqual(store, { enableAutomator: true, theme: 'changed while disconnected' });
    assert.equal((await restoreAutomation(adb, dir)).restored, false);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('test path guard accepts session files and rejects daily data and sibling prefixes', () => {
  assert.doesNotThrow(() => assertDesktopTestPath(join(desktopTests, 'session', 'data', 'backup.zip')));
  for (const path of [join(localRoot, 'desktop', 'data'), desktopTests,
    join(desktopTests, '..', 'android', 'session'), `${desktopTests}-other/session`]) {
    assert.throws(() => assertDesktopTestPath(path), /isolated Desktop test path/);
  }
});

test('recovery validates device identity before writes and retains record until verified', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-recovery-test-'));
  let store: Record<string, unknown> = { enableAutomator: false, automatorMode: 2, theme: 'light' };
  let disconnected = false, ignoreWrites = false, writes = 0;
  let staged: Record<string, unknown> | undefined;
  const adb: Adb = async (...args) => {
    if (disconnected) throw new Error('offline');
    if (args[0] === 'get-serialno') return Buffer.from('device-a');
    if (args[0] === 'push') {
      writes++;
      if (!ignoreWrites) staged = JSON.parse(await readFile(args[1]!, 'utf8'));
    }
    if (args[1] === 'mv' && !ignoreWrites) store = staged!;
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => { disconnected = true; }, dir), /recovery required/);
    disconnected = false;
    const pending = await automationRecoveryStatus(adb, dir);
    await writeFile(pending.path, JSON.stringify({ ...pending.record, serial: 'device-b' }));
    const before = writes;
    await assert.rejects(restoreAutomation(adb, dir), /identity mismatch/);
    assert.equal(writes, before);
    // Diagnostic report location is not required to recover device settings.
    await writeFile(pending.path, JSON.stringify({ ...pending.record, captureDirectory: undefined }));
    ignoreWrites = true;
    await assert.rejects(restoreAutomation(adb, dir), /Restore mismatch/);
    await access(pending.path);
    ignoreWrites = false;
    store.theme = 'dark';
    await restoreAutomation(adb, dir);
    assert.deepEqual(store, { enableAutomator: false, automatorMode: 2, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('profile extraction imports device data and rejects traversal', async () => {
  await mkdir(desktopTests, { recursive: true });
  const root = await mkdtemp(join(desktopTests, 'profile-'));
  const files = {
    'profile.json': strToU8(JSON.stringify({ width: 393, height: 852, fontScale: 1 })),
    'apps.json': strToU8('{"apps":[]}'), 'settings.json': strToU8('{}'),
    'seed.json': strToU8('{"blocked":["example.app"],"subscriptions":{"42":false}}'),
    'subscriptions/42.json': strToU8('{"id":42,"name":"fixture","version":1}'),
  };
  try {
    await assert.rejects(extractProfile(zipSync({ ...files, '../escape.json': strToU8('{}') }), root), /Unexpected/);
    await extractProfile(zipSync(files), root);
    assert.equal(JSON.parse(await readFile(join(root, 'seed.json'), 'utf8')).subscriptions['42'], false);
    assert.equal(JSON.parse(await readFile(join(root, 'subscriptions/42.json'), 'utf8')).name, 'fixture');
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('profile publication restores previous profile on failure and removes stale files on success', async () => {
  await mkdir(desktopTests, { recursive: true });
  const root = await mkdtemp(join(desktopTests, 'profile-publish-'));
  const output = join(root, 'profile'), staged = join(root, 'staged');
  try {
    await mkdir(output); await writeFile(join(output, 'old.json'), 'original');
    await assert.rejects(publishProfile(staged, output));
    assert.equal(await readFile(join(output, 'old.json'), 'utf8'), 'original');
    await mkdir(staged); await writeFile(join(staged, 'profile.json'), 'new');
    await publishProfile(staged, output);
    assert.equal(await readFile(join(output, 'profile.json'), 'utf8'), 'new');
    await assert.rejects(access(join(output, 'old.json')));
  } finally { await rm(root, { recursive: true, force: true }); }
});
