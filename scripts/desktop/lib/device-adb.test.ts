import assert from 'node:assert/strict';
import childProcess, { type ExecFileOptions } from 'node:child_process';
import { syncBuiltinESMExports } from 'node:module';
import { join } from 'node:path';
import { test, type TestContext } from 'node:test';
import { promisify } from 'node:util';
import { createAdb } from './device-adb.ts';

function replaceExec(t: TestContext, run: (file: string, args: string[], options: ExecFileOptions) => Promise<{ stdout: Buffer; stderr: Buffer }>) {
  const original = childProcess.execFile;
  childProcess.execFile = Object.assign(() => { throw new Error('Expected promisified execFile'); }, {
    [promisify.custom]: run,
  }) as unknown as typeof childProcess.execFile;
  syncBuiltinESMExports();
  t.after(() => { childProcess.execFile = original; syncBuiltinESMExports(); });
}

function setSdk(t: TestContext, home: string | undefined, root: string | undefined) {
  const originalHome = process.env.ANDROID_HOME;
  const originalRoot = process.env.ANDROID_SDK_ROOT;
  const restore = (key: string, value: string | undefined) => {
    if (value === undefined) delete process.env[key]; else process.env[key] = value;
  };
  restore('ANDROID_HOME', home); restore('ANDROID_SDK_ROOT', root);
  t.after(() => { restore('ANDROID_HOME', originalHome); restore('ANDROID_SDK_ROOT', originalRoot); });
}

test('adb keeps binary screenshots intact and targets the requested serial without changing PATH', async t => {
  const screenshot = Buffer.alloc(2 * 1024 * 1024, 0xff);
  screenshot[0] = 0;
  const path = process.env.PATH;
  replaceExec(t, async (file, args, options) => {
    assert.equal(file, 'adb');
    assert.deepEqual(args, ['-s', 'test-device', 'exec-out', 'screencap', '-p']);
    assert.equal(options.encoding, 'buffer');
    assert.ok(options.maxBuffer! > screenshot.length, 'Real screenshots can exceed execFile’s default buffer');
    assert.ok(options.timeout! > 0, 'Disconnected devices must not leave commands hanging');
    return { stdout: screenshot, stderr: Buffer.alloc(0) };
  });
  assert.deepEqual(await createAdb('test-device')('exec-out', 'screencap', '-p'), screenshot);
  assert.equal(process.env.PATH, path);
});

test('adb falls back to the configured SDK only when missing from PATH and reuses that executable', async t => {
  setSdk(t, 'primary-sdk', 'secondary-sdk');
  const path = process.env.PATH;
  const calls: string[] = [];
  replaceExec(t, async file => {
    calls.push(file);
    if (file === 'adb') throw Object.assign(new Error('Missing executable'), { code: 'ENOENT' });
    return { stdout: Buffer.from('device'), stderr: Buffer.alloc(0) };
  });
  const executable = (sdk: string) => join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  const adb = createAdb();
  await adb('devices'); await adb('get-serialno');
  assert.deepEqual(calls, ['adb', executable('primary-sdk'), executable('primary-sdk')]);
  delete process.env.ANDROID_HOME;
  calls.length = 0;
  await createAdb()('devices');
  assert.deepEqual(calls, ['adb', executable('secondary-sdk')]);
  assert.equal(process.env.PATH, path);
});

test('adb command failures propagate without retrying an operation on another executable', async t => {
  setSdk(t, 'unused-sdk', undefined);
  let calls = 0;
  const failure = Object.assign(new Error('Device offline'), { code: 1 });
  replaceExec(t, async () => { calls++; throw failure; });
  await assert.rejects(createAdb('test-device')('shell', 'input', 'tap', '1', '2'), error => error === failure);
  assert.equal(calls, 1);
});
