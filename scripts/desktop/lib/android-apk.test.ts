import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseApkBadging } from './android-apk.ts';

test('APK identity reads literal and localized labels and deduplicates the actual names', () => {
  assert.deepEqual(parseApkBadging("package: name='li.gkd.a11y.fixture' versionCode=''\r\napplication: label='GKD accessibility fixture' icon=''\r\n", 'li.gkd.a11y.fixture'), {
    packageName: 'li.gkd.a11y.fixture', labels: ['GKD accessibility fixture'],
  });
  assert.deepEqual(parseApkBadging([
    "package: name='li.songe.gkd.debug' versionCode='94'",
    "application-label:'GKD-debug'", "application-label-zh-CN:'GKD 调试'",
    "application-label-zh:'GKD 调试'", "application: label='GKD-debug' icon='res/icon.png'",
  ].join('\n'), 'li.songe.gkd.debug'), { packageName: 'li.songe.gkd.debug', labels: ['GKD-debug', 'GKD 调试'] });
});

test('APK identity refuses wrong or ambiguous packages and missing display names before installation', () => {
  for (const badging of [
    "package: name='another.app'\napplication-label:'GKD accessibility fixture'",
    "application-label:'GKD accessibility fixture'",
    "package: name='target.app'\npackage: name='other.app'\napplication-label:'GKD'",
  ]) assert.throws(() => parseApkBadging(badging, 'target.app'), /does not match/);
  for (const badging of ["package: name='target.app'", "package: name='target.app'\napplication-label:''\napplication: label='  ' icon=''"
  ]) assert.throws(() => parseApkBadging(badging, 'target.app'), /no readable application label/);
});
