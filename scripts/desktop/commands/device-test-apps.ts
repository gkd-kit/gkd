import { mkdir, mkdtemp } from 'node:fs/promises';
import { join } from 'node:path';
import { cleanupDeviceTestApps, deviceTestAppsStatus } from '../lib/device-test-apps.ts';
import { withAutomationPaused } from '../lib/device-automation.ts';
import { repositoryRoot } from '../lib/local-paths.ts';
import { createAdb } from '../lib/device-adb.ts';

export async function deviceTestApps(serial: string, operation: string) {
  if (!['status', 'cleanup'].includes(operation)) throw new Error('Expected status or cleanup');
  const adb = createAdb(serial);
  const status = await deviceTestAppsStatus(adb);
  if (operation === 'status') return status;
  if (!status.record) return { removed: [], path: status.path };
  const parent = join(repositoryRoot, '.temp', 'test-app-cleanup');
  await mkdir(parent, { recursive: true });
  const folder = await mkdtemp(join(parent, 'round-'));
  // Once cleanup starts, finish its bounded ADB operations and restore settings even on Ctrl+C.
  const interrupt = () => console.error('Finishing test app cleanup and restoring device settings.');
  process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
  try { return await withAutomationPaused(adb, folder, () => cleanupDeviceTestApps(adb)); }
  finally { process.off('SIGINT', interrupt); process.off('SIGTERM', interrupt); }
}
