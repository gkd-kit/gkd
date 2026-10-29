import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { createAdb } from '../lib/device-adb.ts';
import { withAutomationPaused, enableDebugAccessibility, readStore } from '../lib/device-automation.ts';
import { withInstallConfirmation } from '../lib/device-install.ts';
import { installGkdDebug } from '../lib/device-test-apps.ts';
import { withExistingDebugConnection } from '../lib/device-debug-connection.ts';
import { DesktopClient, until } from '../lib/desktop-client.ts';
import { summarizeMatching } from './summarize-matching.ts';

export async function checkDiagnosticPerformance(serial: string, appId: string, output: string) {
  assert.match(appId, /^[a-zA-Z0-9_.]+$/);
  const folder = resolve(output);
  await mkdir(folder, { recursive: true });
  const adb = withInstallConfirmation(createAdb(serial));
  const pkg = 'li.songe.gkd.debug';
  await withAutomationPaused(adb, folder, async original => {
    await writeFile(`${folder}/original.json`, JSON.stringify(original));
    await installGkdDebug(adb);
    await enableDebugAccessibility(adb);
    await adb('shell', 'am', 'start', '-n', `${pkg}/li.gkd.app.MainActivity`);
    await pause(1000);
    await withExistingDebugConnection(adb, AbortSignal.timeout(240_000), async ({ port, token }) => {
      const client = new DesktopClient(port);
      const request = (path: string, body?: unknown) => client.json<any>(path, body,
        { headers: { Authorization: `Bearer ${token}` }, timeoutMillis: 125_000 });
      const component = (await adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', appId)).toString().trim().split(/\r?\n/).at(-1)!;
      assert.ok(component.startsWith(`${appId}/`));
      await adb('shell', 'am', 'start', '-n', component);
      await pause(2500);
      await until(() => request('debug/readiness'), r => r.checks.androidBenchmark.ready && r.runtime.foreground.foreground.confirmed.appId === appId, 'Benchmark readiness');
      await writeFile(`${folder}/target.png`, await adb('exec-out', 'screencap', '-p'));
      const rule = `-98/2/${appId}/1/1`;
      const subscription = JSON.stringify({ id: -98, name: 'read-only diagnostics comparison', version: 1,
        apps: [{ id: appId, groups: [{ key: 1, name: 'scan', rules: [{ key: 1, fastQuery: false, matches: '[text="__gkd_diagnostic_missing__"]' }] }] }] });
      const query = { requestId: 'comparison', rule, subscription, execute: false };
      const benchmark = await request('debug/android/benchmark', { request: query, iterations: 3, warmup: 1, cacheModes: ['Cold'] });
      await writeFile(`${folder}/benchmark.json`, JSON.stringify(benchmark, null, 2));
      console.log('Benchmark:', benchmark.samples.map((s: any) => ({ level: s.level, medianMillis: s.medianNanos / 1e6, p95Millis: s.p95Nanos / 1e6 })));
      const session = await request('debug/sessions', { detailed: true, recordQueries: true, recordSystem: true });
      try {
        for (let i = 0; i < 3; i++) {
          const requestId = `scan-${i}`;
          await request(`debug/sessions/${session.id}/runs`, { ...query, requestId });
          await until(() => request(`debug/sessions/${session.id}/runs/${requestId}`), r => ['completed', 'failed', 'cancelled'].includes(r.phase), 'Query completion');
        }
      } finally { await request(`debug/sessions/${session.id}/stop`, {}); }
      const capture = await request(`debug/sessions/${session.id}/export`, {});
      const file = `${folder}/capture.json`;
      await writeFile(file, JSON.stringify(capture));
      await summarizeMatching(file, `${folder}/summary.json`);
      assert.equal(capture.queryRecordings.length, 3);
      assert.ok(capture.queryRecordings.every((q: any) => q.completed && q.durationNanos > 0 && q.outcome === 'not_matched' && q.incomplete === null));
      assert.equal(capture.session.openOperations.length, 0);
      console.log('Capture:', { dropped: capture.session.dropped, events: capture.events.length,
        queries: capture.queryRecordings.map((q: any) => ({ millis: q.durationNanos / 1e6, calls: q.calls.length, operations: q.operations.length })) });
    });
  }, undefined, { accessibilityForDiagnostics: true });
  const restored = await readStore(adb);
  await writeFile(`${folder}/restored.json`, JSON.stringify(restored));
  return { output: folder };
}
