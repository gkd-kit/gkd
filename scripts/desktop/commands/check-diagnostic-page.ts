import assert from 'node:assert/strict';
import { unzipSync } from 'fflate';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { createAdb } from '../lib/device-adb.ts';
import { withAutomationPaused, readStore, enableDebugAccessibility } from '../lib/device-automation.ts';
import { withInstallConfirmation } from '../lib/device-install.ts';
import { installGkdDebug } from '../lib/device-test-apps.ts';
import { withExistingDebugConnection } from '../lib/device-debug-connection.ts';
import { DesktopClient, until } from '../lib/desktop-client.ts';
import { debugNotificationTarget } from './check-debug-service-stop.ts';

// A focused device regression: real controls, notification PendingIntents and saved capture.
export async function checkDiagnosticPage(serial: string, output: string, appId?: string) {
  const folder = resolve(output);
  await mkdir(folder, { recursive: true });
  const adb = withInstallConfirmation(createAdb(serial));
  const pkg = 'li.songe.gkd.debug';
  const service = `${pkg}/li.gkd.app.service.MatchDiagnosticService`;
  const signal = AbortSignal.timeout(420_000);
  const dump = async (name: string) => {
    const remote = `/sdcard/gkd-diagnostic-${name}.xml`;
    let result = '';
    for (let attempt = 0; attempt < 3; attempt++) {
      result = (await adb('shell', 'uiautomator', 'dump', remote, '2>&1')).toString();
      if (result.includes(remote)) break;
      await pause(500);
    }
    assert.ok(result.includes(remote), `UI dump failed: ${result}`);
    const xml = (await adb('shell', 'cat', remote)).toString();
    await adb('shell', 'rm', remote);
    await writeFile(`${folder}/${name}.xml`, xml);
    return xml;
  };
  const tapText = async (text: string, name: string, long = false) => {
    const xml = await dump(name);
    const tags = [...xml.matchAll(/<node\b[^>]*>/g)].map(m => m[0]);
    const matches = tags.filter(t => t.includes(`text="${text}"`) || t.includes(`content-desc="${text}"`));
    assert.equal(matches.length, 1, `Expected unique visible ${text}`);
    const bounds = matches[0]!.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/)!.slice(1).map(Number);
    const x = String((bounds[0]! + bounds[2]!) / 2), y = String((bounds[1]! + bounds[3]!) / 2);
    if (long) await adb('shell', 'input', 'swipe', x, y, x, y, '1000');
    else await adb('shell', 'input', 'tap', x, y);
    await pause(300);
  };
  const screenshot = async (name: string) => writeFile(`${folder}/${name}.png`, await adb('exec-out', 'screencap', '-p'));
  const openNotification = async (title: string, targetText: string, name: string) => {
    for (let attempt = 0; attempt < 4; attempt++) {
      await tapText(title, `${name}-${attempt}`);
      // MIUI can return the previous notification tree while the shade is closing.
      // Let that transition finish before another tap could hit the underlying page.
      await pause(1200);
      const xml = await dump(`${name}-result-${attempt}`);
      if (xml.includes(`package="${pkg}"`) && xml.includes(`text="${targetText}"`)) return;
      assert.ok(xml.includes('package="com.android.systemui"') && xml.includes(`text="${title}"`), 'Notification opened an unexpected page');
    }
    throw new Error(`Notification did not open ${targetText}`);
  };
  await withAutomationPaused(adb, folder, async original => {
    await writeFile(`${folder}/original.json`, JSON.stringify({ enableAutomator: original.enableAutomator, automatorMode: original.automatorMode }));
    await installGkdDebug(adb);
    if (appId) await enableDebugAccessibility(adb);
    await adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', 'gkd://page/matching-diagnostics', '-n', `${pkg}/li.gkd.app.MainActivity`);
    try {
      await pause(1200);
      await withExistingDebugConnection(adb, signal, async ({ port, token }) => {
        const client = new DesktopClient(port);
        const request = async (path: string, body?: unknown) => {
          const response = await client.response(path, body, { headers: { Authorization: `Bearer ${token}` } });
          assert.equal(response.status, 200, response.body.toString());
          return JSON.parse(response.body.toString());
        };
        const existingRecords = (await adb('shell', 'ls', `/sdcard/Android/data/${pkg}/files/matching-diagnostics`)).toString().trim().split(/\s+/).filter(Boolean);
        const sharedPath = `/sdcard/Android/data/${pkg}/cache/shared`;
        const sharedNames = async () => (await adb('shell', 'ls', sharedPath)).toString().trim().split(/\s+/).filter(Boolean);
        const verifyArchive = async (before: string[], ids: string[], label: string) => {
          const created = (await sharedNames()).filter(name => !before.includes(name));
          assert.equal(created.length, 1, 'Share must create exactly one archive');
          assert.match(created[0]!, /^gkd-log-\d{14}(?:-\d+)?\.zip$/);
          const data = await adb('exec-out', 'cat', `${sharedPath}/${created[0]}`);
          await writeFile(`${folder}/${label}.zip`, data);
          const entries = unzipSync(data);
          assert.ok(Object.keys(entries).some(name => name.startsWith("metadata/")));
          const captures = Object.entries(entries).filter(([name]) => name.startsWith('matching-diagnostics/') && name.endsWith('/diagnostic.json'));
          assert.deepEqual(captures.map(([, bytes]) => JSON.parse(Buffer.from(bytes).toString()).session.id).sort(), [...ids].sort());
        };
        await screenshot('idle');
        await tapText('开启诊断', 'before-start');
        assert.notEqual((await request('debug/readiness')).session?.active, true, 'Opening notice must not start capture');
        await tapText('取消', 'notice-cancel');
        assert.notEqual((await request('debug/readiness')).session?.active, true, 'Cancelling notice must leave capture stopped');
        await tapText('开启诊断', 'before-confirm');
        await screenshot('start-notice');
        await tapText('开始采集', 'notice-start');
        const ready = await until(() => request('debug/readiness'), r => r.session?.active === true, 'UI starts capture');
        const id = ready.session.id;
        assert.match((await adb('shell', 'dumpsys', 'activity', 'services', service)).toString(), /isForeground=true/);
        await screenshot('capturing');
        if (appId) {
          assert.match(appId, /^[a-zA-Z0-9_.]+$/);
          const component = (await adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', appId)).toString().trim().split(/\r?\n/).at(-1)!;
          assert.ok(component.startsWith(`${appId}/`), 'Target app must be installed');
          await adb('shell', 'am', 'start', '-n', component);
          await pause(2500);
          await until(() => request('debug/readiness'), r => r.checks.liveQuery.ready, 'Accessibility query readiness');
          await screenshot('target-app');
          const runs = [];
          for (let index = 0; index < 3; index++) {
            const requestId = `node-scan-${index}`;
            const subscription = JSON.stringify({ id: -98, name: 'diagnostic node scan', version: 1,
              apps: [{ id: appId, groups: [{ key: 1, name: 'read-only scan', rules: [{ key: 1, fastQuery: false,
                matches: '[text="__gkd_diagnostic_missing__"]' }] }] }] });
            await request(`debug/sessions/${id}/runs`, { requestId, rule: `-98/2/${appId}/1/1`, subscription, execute: false });
            runs.push(await until(() => request(`debug/sessions/${id}/runs/${requestId}`), r => ['completed', 'failed', 'cancelled'].includes(r.phase), 'Read-only node scan completes'));
          }
          await writeFile(`${folder}/target-runs.json`, JSON.stringify(runs, null, 2));
          await writeFile(`${folder}/target-capture.json`, JSON.stringify(await request(`debug/sessions/${id}/export`, {})));
        }
        await adb('shell', 'input', 'keyevent', 'KEYCODE_HOME');
        await pause(700);
        await adb('shell', 'cmd', 'statusbar', 'expand-notifications');
        await pause(500);
        await screenshot('notification');
        await openNotification('正在采集', '诊断采集', 'notification-open');
        await screenshot('notification-diagnostics');
        // Re-enter the existing diagnostic page through its notification.
        await adb('shell', 'cmd', 'statusbar', 'expand-notifications');
        await pause(500);
        await openNotification('正在采集', '诊断采集', 'notification-from-records');
        assert.equal((await request(`debug/sessions/${id}`)).active, true);
        await adb('shell', 'cmd', 'statusbar', 'expand-notifications');
        await pause(500);
        let stopped = false;
        for (let step = 0; step < 5; step++) {
          // Reuse the existing vendor-aware notification targeting, scoped to this title.
          const xml = await dump(`notification-stop-${step}`);
          const target = debugNotificationTarget(xml.replaceAll('调试服务已开启', '调试连接').replaceAll('正在采集', '调试服务已开启'));
          assert.ok(target, 'Cannot identify diagnostic notification stop/expand control');
          console.log(`Diagnostic notification: ${target.kind}`);
          if (target.kind === 'expand-long-press') await adb('shell', 'input', 'swipe', `${target.x}`, `${target.y}`, `${target.x}`, `${target.y}`, '1500');
          else await adb('shell', 'input', 'tap', `${target.x}`, `${target.y}`);
          await pause(1000);
          if (target.kind === 'stop') { stopped = true; break; }
        }
        assert.ok(stopped, 'Stop action not reached');
        await until(() => request(`debug/sessions/${id}`), s => !s.active, 'notification stops capture');
        await until(async () => (await adb('shell', 'dumpsys', 'activity', 'services', service)).toString(), s => !s.includes('isForeground=true'), 'foreground service exits');
        const capture = await request(`debug/sessions/${id}/export`, {});
        assert.equal(capture.session.id, id);
        const files = (await adb('shell', 'ls', `/sdcard/Android/data/${pkg}/files/matching-diagnostics`)).toString();
        const captureDirectory = files.trim().split(/\s+/).find(name => name === String(capture.session.startedAt));
        assert.ok(captureDirectory);
        const savedCapture = JSON.parse((await adb('shell', 'cat', `/sdcard/Android/data/${pkg}/files/matching-diagnostics/${captureDirectory}/diagnostic.json`)).toString());
        assert.equal(savedCapture.session.id, id);
        await writeFile(`${folder}/capture.json`, JSON.stringify(capture));
        // MIUI keeps its notification long-press modal after the action has stopped the service.
        await adb('shell', 'input', 'keyevent', 'KEYCODE_BACK');
        await adb('shell', 'cmd', 'statusbar', 'collapse');
        await adb('shell', 'cmd', 'statusbar', 'expand-notifications');
        await pause(500);
        await screenshot('saved-notification');
        await openNotification('已保存诊断记录', '诊断采集', 'saved-notification-open');
        await pause(700);
        const savedUi = await dump('saved-ui');
        assert.ok(savedUi.includes('text="诊断采集"'), 'Diagnostic page must contain capture controls');
        assert.ok(!savedUi.includes('当前引擎') && !savedUi.includes('运行记录'), 'Technical details must not appear on the user page');
        const cardTime = async (startedAt: number) => (await adb('shell', 'date', '-d', `@${Math.floor(startedAt / 1000)}`, "'+%m-%d %H:%M:%S'")).toString().trim();
        const savedTime = await cardTime(capture.session.startedAt);
        assert.ok(savedUi.includes(savedTime), 'Saved capture must appear in the record list');
        await screenshot('saved');
        await tapText(savedTime, 'open-record');
        await pause(500);
        assert.ok((await dump('record-preview')).includes('分享'));
        await screenshot('record-preview');
        const singleBefore = await sharedNames();
        await tapText('分享', 'share-record');
        await pause(1000);
        await verifyArchive(singleBefore, [id], 'single-export');
        await screenshot('share-record');
        assert.match(await dump('share-record-ui'), /ResolverActivity|ChooserActivity|导出诊断|分享|发送/);
        await writeFile(`${folder}/share-record-window.txt`, (await adb('shell', 'dumpsys', 'window', 'windows')).toString());
        await adb('shell', 'input', 'keyevent', 'KEYCODE_BACK');
        await tapText(savedTime, 'reopen-single');
        await tapText('删除', 'delete-single');
        assert.match(await dump('delete-single-confirm'), /删除选中的 1 条诊断记录/);
        await tapText('删除', 'delete-single-confirm-action');
        assert.ok(!(await dump('single-deleted')).includes(savedTime));
        const timed = await request('debug/sessions', { recordSystem: true });
        await pause(61_000);
        assert.equal((await request(`debug/sessions/${timed.id}`)).active, true, 'Capture must remain active after 60 seconds');
        await adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', 'gkd://page/matching-diagnostics', '-n', `${pkg}/li.gkd.app.MainActivity`);
        await tapText('关闭诊断', 'page-stop');
        await until(async () => (await adb('shell', 'dumpsys', 'activity', 'services', service)).toString(), s => !s.includes('isForeground=true'), 'manual stop removes notification');
        const notifications = (await adb('shell', 'dumpsys', 'notification', '--noredact')).toString().split('Historical notifications')[0]!;
        assert.ok(!notifications.includes('android.title=String (已保存诊断记录)'), 'Page stop must not post a completion notification');
        const extra = await request('debug/sessions', { recordSystem: true });
        await request(`debug/sessions/${extra.id}/stop`, {});
        await until(() => request('debug/readiness'), r => r.session?.active === false, 'second stopped record');
        await pause(500);
        await tapText(await cardTime(timed.startedAt), 'select-first', true);
        await tapText(await cardTime(extra.startedAt), 'select-second');
        assert.match(await dump('selected-two'), /已选 2 项/);
        await screenshot('selected-two');
        await tapText('更多', 'batch-menu');
        const batchBefore = await sharedNames();
        await tapText('分享', 'batch-share');
        await pause(700);
        await verifyArchive(batchBefore, [timed.id, extra.id], 'batch-export');
        await screenshot('batch-share');
        await writeFile(`${folder}/batch-share-window.txt`, (await adb('shell', 'dumpsys', 'window', 'windows')).toString());
        await dump('batch-share-ui');
        await adb('shell', 'input', 'keyevent', 'KEYCODE_BACK');
        await tapText('更多', 'batch-menu-delete');
        await tapText('删除', 'batch-delete');
        assert.match(await dump('batch-delete-confirm'), /删除选中的 2 条诊断记录/);
        await tapText('删除', 'batch-delete-confirm-action');
        const remaining = (await adb('shell', 'ls', `/sdcard/Android/data/${pkg}/files/matching-diagnostics`)).toString().trim().split(/\s+/).filter(Boolean);
        assert.deepEqual(remaining.sort(), existingRecords.sort(), 'Only captures created by this test may be deleted');
        await screenshot('after-deletion');
        const invalid = await client.response('debug/sessions', { detailed: true }, { headers: { Authorization: `Bearer ${token}` } });
        assert.equal(invalid.status, 400, 'Service startup must preserve invalid-argument HTTP status');
        await until(async () => (await adb('shell', 'dumpsys', 'activity', 'services', service)).toString(), s => !s.includes('isForeground=true'), 'failed startup removes notification');
        await writeFile(`${folder}/report.json`, JSON.stringify({ id, timedId: timed.id, notificationStop: true, notificationReturn: true, existingAdvancedEntryReturn: true, savedNotificationOpen: true, pageStopWithoutNotification: true, saved: true, singleShare: true, singleDelete: true, batchShare: true, batchDelete: true, existingRecordsPreserved: true, noAutomaticStop: true }, null, 2));
      });
    } catch (error) {
      console.error('Diagnostic page verification failed:', error);
      throw error;
    } finally {
      await adb('shell', 'cmd', 'statusbar', 'collapse');
      for (const target of [service, `${pkg}/li.gkd.app.service.DebugService`]) {
        const running = (await adb('shell', 'dumpsys', 'activity', 'services', target)).toString();
        if (running.includes('ServiceRecord{')) {
          try { await adb('shell', 'run-as', pkg, 'am', 'stopservice', '--user', '0', '-n', target); }
          catch (error) {
            // This device's run-as am returns 255 even for a successful stop.
            const stderr = (error as { stderr?: Buffer }).stderr?.toString().trim();
            if (stderr !== 'Service stopped' && stderr !== 'Service not stopped: was not running.') throw error;
          }
          await until(async () => (await adb('shell', 'dumpsys', 'activity', 'services', target)).toString(), s => !s.includes('isForeground=true'), 'service cleanup');
        }
      }
    }
  }, undefined, { accessibilityForDiagnostics: !!appId });
  const restored = await readStore(adb);
  await writeFile(`${folder}/restored.json`, JSON.stringify({ enableAutomator: restored.enableAutomator, automatorMode: restored.automatorMode }));
  return { output: folder };
}
