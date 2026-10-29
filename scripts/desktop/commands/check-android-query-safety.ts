import assert from 'node:assert/strict';
import { createDebugWait, waitForDebugRun, type MatchDebugRun as Run } from '../lib/match-debug-runs.ts';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { repositoryRoot } from '../lib/local-paths.ts';
import { DesktopClient } from '../lib/desktop-client.ts';
import type { Adb } from '../lib/device-automation.ts';
import type { DebugReadiness } from '../lib/debug-readiness.ts';
import { summarizeMatching } from './summarize-matching.ts';
import { ensureDeviceTestApp } from '../lib/device-test-apps.ts';

const fixture = 'li.gkd.a11y.fixture';
const activity = `${fixture}/.FixtureActivity`;

type Capture = { session: { id: string; dropped: number }; events: any[]; entries: { source: string; reason: string; count: number }[] };

export async function checkAndroidQuerySafety(adb: Adb, port: number, token: string, output: string, signal: AbortSignal, apk: string) {
  await mkdir(dirname(output), { recursive: true });
  const directory = join(dirname(output), 'query-safety-artifacts');
  await mkdir(directory, { recursive: true });
  const client = new DesktopClient(port);
  const json = <T = any>(path: string, body?: unknown) => client.json<T>(path, body, { headers: { Authorization: `Bearer ${token}` } });
  const until = createDebugWait(signal, 20000, 50, 'Device query check');
  const report: { device?: unknown; installation?: unknown; results: any[]; error?: string } = { results: [] };
  let owned = false;
  try {
    const remoteApk = (await adb('shell', 'pm', 'path', 'li.songe.gkd.debug')).toString().split(/\r?\n/)
      .find(line => line.startsWith('package:') && line.endsWith('/base.apk'))?.slice('package:'.length);
    assert.ok(remoteApk, 'Cannot identify installed GKD APK');
    const installedHash = (await adb('shell', 'sha256sum', remoteApk)).toString().split(/\s+/)[0];
    const localApkDir = join(repositoryRoot, 'gkd-android/build/outputs/apk/gkd/debug');
    const localApks = (await readdir(localApkDir)).filter(name => name.endsWith('.apk'));
    assert.equal(localApks.length, 1);
    const localHash = createHash('sha256').update(await readFile(join(localApkDir, localApks[0]!))).digest('hex');
    assert.equal(installedHash, localHash, 'Install the current gkd Debug APK with --install before checking');
    report.device = { serial: (await adb('get-serialno')).toString().trim(),
      sdk: (await adb('shell', 'getprop', 'ro.build.version.sdk')).toString().trim(),
      model: (await adb('shell', 'getprop', 'ro.product.model')).toString().trim(), apkSha256: installedHash };
    report.installation = await ensureDeviceTestApp(adb, fixture, apk); owned = true;
    console.log('Test app:', report.installation);
    const app = await json('debug/app');
    assert.equal(app.enableMatch, false);
    assert.equal(app.automatorMode, 1);
    const rule = '-98/2/li.gkd.a11y.fixture/1/1';
    const request = (id: string, exclude: boolean) => ({ requestId: id, rule, execute: true,
      subscription: JSON.stringify({ id: -98, name: 'Query safety', version: 1, apps: [{ id: fixture, groups: [{ key: 1, name: 'Query safety',
        rules: [{ key: 1, matches: '[depth=0]', matchRoot: true, fastQuery: false, action: 'clickCenter', actionCd: 0,
          ...(exclude ? { excludeMatches: ['[desc="forbidden"]'] } : {}) }] }] }] }) });
    async function launch(scenario: string, children: number, groups: number, forbidden: boolean) {
      await adb('shell', 'am', 'start', '-n', activity, '--es', 'scenario', scenario, '--ei', 'children', String(children), '--ei', 'groups', String(groups), '--ez', 'forbidden', String(forbidden));
      await until(() => json<DebugReadiness>('debug/readiness'), value => value.checks.liveQuery.ready && !value.runtime.foreground?.locked && value.runtime.foreground?.foreground.confirmed.appId === fixture);
      await until(() => json<{ apps: { id: string }[] }>('debug/app/catalog'), value => value.apps.some(app => app.id === fixture));
    }
    async function state() {
      await adb('shell', 'am', 'start', '-n', activity, '--es', 'operation', 'report');
      return JSON.parse((await adb('shell', 'run-as', fixture, 'cat', 'files/state.json')).toString());
    }
    const terminal = (base: string, id: string) => waitForDebugRun(() => json<Run>(`${base}/runs/${id}`), until, 120000);
    for (const scenario of [
      { id: 'complete-negative', children: 16, groups: 1, forbidden: false, reason: 'action_finished', touches: 1 },
      { id: 'complete-excluded', children: 16, groups: 1, forbidden: true, reason: 'not_matched', touches: 0 },
      { id: 'child-limit', children: 513, groups: 1, forbidden: true, reason: 'query_child_limit', touches: 0 },
      { id: 'descendant-limit', children: 128, groups: 33, forbidden: true, reason: 'query_descendant_limit', touches: 0 },
    ]) {
      await launch(scenario.id, scenario.children, scenario.groups, scenario.forbidden);
      const session = await json('debug/sessions', { });
      const base = `debug/sessions/${session.id}`;
      try {
        await json(`${base}/runs`, request(scenario.id, true));
        const result = await terminal(base, scenario.id);
        const physical = await state();
        report.results.push({ scenario, result, physical });
        assert.equal(result.phase, 'completed', JSON.stringify(result));
        assert.equal(result.result?.reason, scenario.reason, JSON.stringify(result));
        assert.equal(physical.downs, scenario.touches, 'Unexpected physical DOWN');
        assert.equal(physical.ups, scenario.touches, 'Unexpected physical UP');
        if (scenario.touches === 0) assert.equal(result.result?.action ?? null, null);
      } finally {
        await json(`${base}/stop`, {});
        const path = join(directory, `${scenario.id}.json`);
        await writeFile(path, JSON.stringify(await json(`${base}/export`, {}), null, 2));
        await summarizeMatching(path, join(directory, `${scenario.id}-summary.json`));
      }
    }
    await launch('pressure', 16, 1, false);
    const session = await json('debug/sessions', { });
    const base = `debug/sessions/${session.id}`;
    try {
      const policy = await json('debug/policy');
      await json('debug/policy', { ...policy, contentMergeMillis: 0 });
      await adb('shell', 'am', 'start', '-n', activity, '--es', 'operation', 'burst', '--ei', 'events', '1500', '--ei', 'stall', '4500');
      const pressureRuns = [];
      for (let index = 0; index < 8; index++) {
        const id = `pressure-${index}`;
        await json(`${base}/runs`, request(id, false));
        pressureRuns.push(await terminal(base, id));
      }
      await pause(5000);
      const physical = await state();
      const capture = await json<Capture>(`${base}/export`, {});
      const drops = capture.events.filter(event => event.eventDrop);
      const pressure = capture.events.filter(event => event.nodePressure);
      report.results.push({ scenario: 'pressure', physical, pressureRuns, drops: drops.length, pressure });
      assert.ok(drops.length > 0, 'No actual event queue overflow was captured');
      assert.ok(pressure.some(event => event.reason === 'node_read_timeout'), 'Missing physical read timeout');
      assert.ok(pressure.some(event => event.reason === 'node_read_capacity'), 'Missing exhausted reader capacity');
      assert.ok(drops.every(event => event.eventDrop.capacity === 64 && event.eventDrop.ageMillis >= 0));
      assert.ok(pressure.some(event => event.nodePressure.activeReads === 4 && event.nodePressure.observedReads > 0));
      await json(`${base}/runs`, request('recovered', false));
      const recovered = await terminal(base, 'recovered');
      const after = await state();
      assert.equal(recovered.result?.action?.result, true, JSON.stringify(recovered));
      assert.equal(after.downs, physical.downs + 1);
      assert.equal(after.ups, physical.ups + 1);
      report.results.push({ scenario: 'recovered', result: recovered, physical: after });
    } finally {
      await json(`${base}/stop`, {});
      const path = join(directory, 'pressure.json');
      await writeFile(path, JSON.stringify(await json(`${base}/export`, {}), null, 2));
      await summarizeMatching(path, join(directory, 'pressure-summary.json'));
    }
    return { output, scenarios: report.results.length };
  } catch (error) { report.error = String(error); throw error; }
  finally {
    try { if (owned) await adb('shell', 'am', 'force-stop', fixture); }
    finally { await writeFile(output, JSON.stringify(report, null, 2)); }
  }
}
