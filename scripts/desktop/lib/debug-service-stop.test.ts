import assert from 'node:assert/strict';
import { test } from 'node:test';
import { debugNotificationTarget } from '../commands/check-debug-service-stop.ts';

const node = (attrs: string, children = '') => `<node package="com.android.systemui" ${attrs}>${children}</node>`;
const title = (text: string) => node(`resource-id="android:id/title" text="${text}"`);
const stop = (x = 0) => node(`resource-id="android:id/action0" text="停止" clickable="true" enabled="true" bounds="[${x},100][${x + 100},150]"`);
const card = (text: string, action: string) => node('resource-id="com.android.systemui:id/expandableNotificationRow"', title(text) + action);
const dump = (...cards: string[]) => `<hierarchy>${node('', cards.join(''))}</hierarchy>`;

test('debug notification Stop is selected from its own card, independently of neighbouring Stop actions', () => {
  assert.deepEqual(debugNotificationTarget(dump(card('Other service', stop(200)), card('调试服务已开启', stop()))),
    { kind: 'stop', x: 50, y: 125 });
  assert.equal(debugNotificationTarget(dump(card('调试服务已开启', ''), card('Other service', stop()))), null);
});

test('collapsed debug notification can expand only its own observed button', () => {
  const expand = node('resource-id="android:id/expand_button" clickable="true" enabled="true" bounds="[100,100][140,140]"');
  assert.deepEqual(debugNotificationTarget(dump(card('调试服务已开启', expand))), { kind: 'expand', x: 120, y: 120 });
  assert.equal(debugNotificationTarget(dump(card('调试服务已开启', ''), card('Other service', expand))), null);
});

test('MIUI collapsed app group expands its observed parent card before looking for the Stop action', () => {
  // Android 13 MIUI exposes the group as clickable, while its child notification titles are not.
  const group = (label: string, children: string) => node('clickable="true" enabled="true" bounds="[39,428][1041,604]"',
    node(`resource-id="com.android.systemui:id/notification_children_container" content-desc="${label}"`, children));
  const children = card('GKD-debug', '') + card('调试服务已开启', '');
  assert.deepEqual(debugNotificationTarget(dump(group('Android 系统', card('USB', '')), group('GKD-debug', children))),
    { kind: 'expand', x: 540, y: 516 });
  assert.equal(debugNotificationTarget(dump(group('Other app', children))), null);
  assert.equal(debugNotificationTarget(dump(group('GKD-debug', children).replace('clickable="true"', 'clickable="false"'))), null);
});

test('MIUI single notification exposes actions by long pressing its card without tapping its content intent', () => {
  const icon = node('resource-id="com.android.systemui:id/app_icon" content-desc="GKD-debug"');
  const expanded = node('resource-id="com.android.systemui:id/expanded" bounds="[39,428][1041,714]"', icon + title('调试服务已开启'));
  const row = node('clickable="true" enabled="true" bounds="[39,428][1041,549]"', expanded);
  assert.deepEqual(debugNotificationTarget(dump(row)), { kind: 'expand-long-press', x: 540, y: 488 });
  const grouped = node('clickable="true" enabled="true" bounds="[39,400][1041,900]"',
    node('resource-id="com.android.systemui:id/notification_children_container" content-desc="GKD-debug"',
      node('resource-id="com.android.systemui:id/collapse_button"') + row + card('GKD-debug', '')));
  assert.deepEqual(debugNotificationTarget(dump(grouped)), { kind: 'expand-long-press', x: 540, y: 488 });
  assert.equal(debugNotificationTarget(dump(grouped.replace(row, card('调试服务已开启', '')))), null);
  assert.equal(debugNotificationTarget(dump(row.replace('content-desc="GKD-debug"', 'content-desc="Other"'))), null);
  assert.equal(debugNotificationTarget(dump(row.replace('clickable="true"', 'clickable="false"'))), null);
});

test('ambiguous, incomplete and disabled notification actions are not clicked', () => {
  assert.equal(debugNotificationTarget(dump(card('调试服务已开启', stop()), card('调试服务已开启', stop(200)))), null);
  assert.equal(debugNotificationTarget(dump(card('调试服务已开启', stop() + stop(200)))), null);
  assert.equal(debugNotificationTarget(dump(card('调试服务已开启', stop().replace('enabled="true"', 'enabled="false"')))), null);
  assert.equal(debugNotificationTarget(dump(card('调试服务已开启', stop())).replace('</hierarchy>', '')), null);
});
