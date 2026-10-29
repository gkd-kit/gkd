import { automationRecoveryStatus, restoreAutomation } from '../lib/device-automation.ts';
import { createAdb } from '../lib/device-adb.ts';

export async function deviceRecovery(serial: string, operation: string) {
  if (!['status', 'restore'].includes(operation)) throw new Error('Expected status or restore');
  const adb = createAdb(serial);
  return operation === 'restore' ? restoreAutomation(adb) : automationRecoveryStatus(adb);
}
