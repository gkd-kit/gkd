import { execFile } from 'node:child_process';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { readAndroidSdk } from './android-apk.ts';
import type { Adb } from './device-automation.ts';

/** Binary-safe, bounded device commands. Cancellation belongs to the caller so recovery can still run. */
export function createAdb(serial?: string): Adb {
  const exec = promisify(execFile);
  const prefix = serial === undefined ? [] : ['-s', serial];
  let executable = 'adb';
  return async (...args) => {
    const run = async () => (await exec(executable, [...prefix, ...args], {
      encoding: 'buffer', windowsHide: true, timeout: 45_000,
      // Screenshots and full UI/window dumps exceed execFile's default 1 MiB buffer.
      maxBuffer: 64 * 1024 * 1024,
    })).stdout;
    try { return await run(); }
    catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT' || executable !== 'adb') throw error;
      const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || await readAndroidSdk();
      executable = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
      return run();
    }
  };
}
