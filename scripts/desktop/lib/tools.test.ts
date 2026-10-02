import { assertDesktopTestPath, desktopTests, localRoot } from './local-paths.ts';
import { zipSync, strToU8 } from 'fflate';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, access, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { extractProfile, publishProfile, withAutomationPaused } from '../commands/capture-profile.ts';
import type { Adb } from '../commands/capture-profile.ts';

test('capture restores only automation fields on failure, preserving intervening settings', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-capture-test-'));
  let store: Record<string, unknown> = { enableAutomator: true, automatorMode: 2, theme: 'light' };
  const adb: Adb = async (...args) => {
    if (args[0] === 'push') store = JSON.parse(await readFile(args[1]!, 'utf8'));
    return Buffer.from(args[1] === 'cat' ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.equal(store.enableAutomator, false); assert.equal(store.automatorMode, 1);
      store.theme = 'dark'; throw new Error('export failed');
    }), /export failed/);
    assert.deepEqual(store, { enableAutomator: true, automatorMode: 2, theme: 'dark' });
    await assert.rejects(access(join(dir, 'automation-recovery.json')));
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('capture retains recovery record when restoration fails and refuses another run', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-capture-test-'));
  let store: Record<string, unknown> = { enableAutomator: true };
  let failRestore = false;
  const adb: Adb = async (...args) => {
    if (args[0] === 'push') {
      if (failRestore) throw new Error('device disconnected');
      store = JSON.parse(await readFile(args[1]!, 'utf8'));
    }
    return Buffer.from(args[1] === 'cat' ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => { failRestore = true; }), /recovery required/);
    const record = await readFile(join(dir, 'automation-recovery.json'), 'utf8');
    assert.deepEqual(JSON.parse(record), { enableAutomator: true });
    await assert.rejects(withAutomationPaused(adb, dir, async () => assert.fail('Must not run')), /EEXIST/);
    assert.equal(await readFile(join(dir, 'automation-recovery.json'), 'utf8'), record);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('test path guard accepts session files and rejects daily data and sibling prefixes', () => {
  assert.doesNotThrow(() => assertDesktopTestPath(join(desktopTests, 'session', 'data', 'backup.zip')));
  for (const path of [join(localRoot, 'desktop', 'data'), desktopTests,
    join(desktopTests, '..', 'android', 'session'), `${desktopTests}-other/session`]) {
    assert.throws(() => assertDesktopTestPath(path), /isolated Desktop test path/);
  }
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
