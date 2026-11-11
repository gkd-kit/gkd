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
  const command = async (args: string[], input?: Buffer) => {
    const run = async () => {
      const pending = exec(executable, [...prefix, ...args], {
        encoding: 'buffer', windowsHide: true, timeout: 45_000,
        // Screenshots and full UI/window dumps exceed execFile's default 1 MiB buffer.
        maxBuffer: 64 * 1024 * 1024,
      });
      if (input !== undefined) {
        // If adb exits early, its command error is reported by execFile.
        pending.child.stdin?.on('error', () => {});
        pending.child.stdin?.end(input);
      }
      return (await pending).stdout;
    };
    try { return await run(); }
    catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT' || executable !== 'adb') throw error;
      const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || await readAndroidSdk();
      executable = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
      return run();
    }
  };
  const adb: Adb = (...args) => command(args);
  adb.withInput = (input, ...args) => command(args, input);
  return adb;
}
