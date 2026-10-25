import assert from 'node:assert/strict';
import { test } from 'node:test';
import { installDevices } from '../commands/device-install.ts';

// adb output is an external protocol; inaccessible entries must never become installation targets.
const listing = `* daemon not running; starting now at tcp:5037
* daemon started successfully
List of devices attached
phone\tdevice product:phone model:Phone transport_id:1
emulator-5554\tdevice
locked\tunauthorized
disconnected\toffline
repair\trecovery
`;

test('device install selects only authorized targets from adb output, preserving order and explicit serial selection', () => {
  const devices = installDevices(listing.replaceAll('\n', '\r\n'));
  assert.deepEqual(devices.selected, ['phone', 'emulator-5554']);
  assert.deepEqual(devices.skipped, [
    { serial: 'locked', state: 'unauthorized' },
    { serial: 'disconnected', state: 'offline' },
    { serial: 'repair', state: 'recovery' },
  ]);
  assert.deepEqual(installDevices(listing, 'emulator-5554').selected, ['emulator-5554']);
});

test('unavailable or empty explicit serials never broaden installation to other devices', () => {
  for (const serial of ['locked', 'disconnected', 'repair', 'missing', '', ' ']) {
    assert.throws(() => installDevices(listing, serial), /not available/);
  }
  assert.throws(() => installDevices('List of devices attached\n'), /No connected/);
  assert.throws(() => installDevices('List of devices attached\nlocked\tunauthorized\n'), /No connected/);
});
