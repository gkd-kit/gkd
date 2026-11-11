import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdir, mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { automationRecoveryStatus, withAutomationPaused, type Adb } from './device-automation.ts';
import { desktopTests } from './local-paths.ts';

test('Debug restores settings in private storage after the installed app migrates', async () => {
  await mkdir(desktopTests, { recursive: true });
  const directory = await mkdtemp(join(desktopTests, 'debug-storage-'));
  let internal = false;
  let store: Record<string, unknown> = { enableAutomator: true, automatorMode: 2 };
  let pending: Record<string, unknown>;
  const adb: Adb = async (...args) => {
    if (args[0] === 'get-serialno') return Buffer.from('debug-device');
    if (args[1] === 'settings') return Buffer.from('null');
    if (args[0] === 'push') {
      assert.ok(!internal, 'Migrated settings must not be written to external storage');
      pending = JSON.parse(await readFile(args[1]!, 'utf8'));
    }
    if (args[1] === 'mv') store = pending;
    if (args[1]?.includes('no_backup/external-migration/journal.json')) {
      return Buffer.from(internal ? '{"coreCommitted":true}' : '');
    }
    return Buffer.from(args[1]?.includes('cat ') ? JSON.stringify(store) : '');
  };
  adb.withInput = async (input, ...args) => {
    assert.match(args.join(' '), /run-as li\.songe\.gkd\.debug .*files\/store\/store\.json/);
    store = JSON.parse(input.toString());
    return Buffer.alloc(0);
  };
  try {
    await withAutomationPaused(adb, directory, async () => {
      assert.equal(store.enableAutomator, false);
      internal = true;
      store.theme = 'dark';
    }, directory);
    assert.deepEqual(store, { enableAutomator: true, automatorMode: 2, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, directory)).record, null);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
