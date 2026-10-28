import assert from 'node:assert/strict';
import { createDebugWait, waitForDebugRun, type MatchDebugRun as Run, type DebugWait } from '../lib/match-debug-runs.ts';
import { mkdir, writeFile } from 'node:fs/promises';
import { basename, dirname, join } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { DesktopClient } from '../lib/desktop-client.ts';
import type { Adb } from '../lib/device-automation.ts';
import type { DebugReadiness } from '../lib/debug-readiness.ts';

type Touch = { action: number; time: number; downTime: number; x: number; y: number; pointers: number };
type Surface = { active: boolean; down: boolean; now: number; bounds: number[]; dropped: number; events: Touch[] };
export type GestureBackend = 'accessibility' | 'privilege' | 'automation';

/** Real MotionEvents on a consuming Debug surface, through the production rule/action lane. */
export async function checkAndroidGestures(adb: Adb, port: number, token: string, output: string, signal: AbortSignal,
  sessionLifetimes = false, backend: GestureBackend = 'accessibility', stopTransport?: () => Promise<string>) {
  const client = new DesktopClient(port);
  const json = async <T>(path: string, body?: unknown) => {
    if (path === 'debug/sessions' && body !== undefined) {
      // Engine idle precedes capture file saving and the capture service's destruction.
      // Wait for that actual lifecycle boundary before starting the next test capture.
      await until(async () => (await adb('shell', 'dumpsys', 'activity', 'services',
        'li.songe.gkd.debug/li.gkd.app.service.MatchDiagnosticService')).toString(),
      services => !services.includes('ServiceRecord{'));
    }
    return client.json<T>(path, body, { headers: { Authorization: `Bearer ${token}` } });
  };
  const until = createDebugWait(signal, 15000, 20, 'Gesture check');
  const app = await json<{ enableMatch: boolean; automatorMode: number }>('debug/app');
  const mode = backend === 'automation' ? 2 : 1;
  assert.equal(app.enableMatch, false); assert.equal(app.automatorMode, mode);
  const activity = 'li.gkd.app.development.GestureDebugActivity';
  await adb('shell', 'am', 'start', '-n', `li.songe.gkd.debug/${activity}`);
  const readiness = await until(() => json<DebugReadiness>('debug/readiness'), s => {
    const foreground = s.runtime.foreground;
    return s.checks.liveQuery.ready && s.checks.startCapture.ready && foreground?.serviceMode === mode && !foreground.locked &&
      foreground.foreground.confirmed.appId === 'li.songe.gkd.debug' &&
      (foreground.foreground.confirmed.activityId == null || foreground.foreground.confirmed.activityId === activity);
  });
  const surface = () => json<Surface>('debug/android/gesture-surface');
  const initial = await until(surface, s => s.active && s.bounds.length === 4 && s.bounds[2]! > 0);
  // Node-confirmed foreground may legitimately have no Activity class. Verify the
  // actual resumed activity through ADB before submitting any touch in that case.
  const activities = (await adb('shell', 'dumpsys', 'activity', 'activities')).toString();
  assert.ok(activities.split('\n').some(line => /mResumedActivity|topResumedActivity/.test(line) && line.includes(activity)),
    'The consuming Debug touch surface must be the resumed activity');
  const [left, top, width, height] = initial.bounds as [number, number, number, number];
  const x = Math.round(left + width * .5), startY = Math.round(top + height * .7), endY = Math.round(top + height * .3);
  if (sessionLifetimes) return checkSessionLifetimes(json, until, surface, { x, startY, endY, activity }, output, backend, readiness,
    stopTransport ? async () => { token = await stopTransport(); } : undefined);
  const session = await json<{ id: string }>('debug/sessions', { detailed: true,
    ruleFilter: '-99/2/li.songe.gkd.debug/1/1', recordSystem: true });
  const base = `debug/sessions/${session.id}`;
  const results: unknown[] = [];
  const report: { results: unknown[]; diagnostics?: unknown; error?: string; cleanupError?: string } = { results };
  const idle = () => until(() => json<{ engine: { activeRunId: number | null } }>('debug/engine'), s => s.engine.activeRunId === null, 3000);
  const terminal = (id: string) => waitForDebugRun(() => json<Run>(`${base}/runs/${id}`), until, 8000);
  try {
    for (const scenario of [
      { id: 'continuous', duration: 1200, end: endY },
      { id: 'cancel-first', duration: 4000, end: endY, cancelAfter: 10 },
      { id: 'after-first', duration: 250, end: endY },
      { id: 'cancel-middle', duration: 4000, end: endY, cancelAfter: 350 },
      { id: 'after-middle', duration: 250, end: endY },
      { id: 'stationary', duration: 400, end: startY },
      { id: 'one-pixel', duration: 450, end: startY - 1 },
      { id: 'cancel-stationary', duration: 4000, end: startY, cancelAfter: 250 },
      { id: 'after-stationary', duration: 250, end: endY },
    ]) {
      await idle(); await json('debug/android/gesture-surface/reset', {});
      await json(`${base}/runs`, { requestId: scenario.id, rule: '-99/2/li.songe.gkd.debug/1/1', execute: true,
        subscription: JSON.stringify({ id: -99, name: 'Gesture check', version: 1, apps: [{ id: 'li.songe.gkd.debug',
          groups: [{ key: 1, name: 'Gesture check', rules: [{ key: 1, matches: '[depth=0]', matchRoot: true,
            activityIds: [activity], action: 'swipe', actionCd: 0, swipeArg: {
              start: { x: String(x), y: String(startY) }, end: { x: String(x), y: String(scenario.end) }, duration: scenario.duration,
            } }] }] }] }) });
      await until(surface, s => s.down, 3000);
      let cancelAt: number | undefined, cancelledAtHost: number | undefined;
      if (scenario.cancelAfter !== undefined) {
        await pause(scenario.cancelAfter);
        cancelAt = (await surface()).now;
        cancelledAtHost = Date.now();
        const cancelled = await json<Run>(`${base}/runs/${scenario.id}/cancel`, {});
        assert.equal(cancelled.phase, 'cancelling');
      }
      const run = await terminal(scenario.id);
      const hostCancelMillis = cancelledAtHost === undefined ? undefined : Date.now() - cancelledAtHost;
      const observed = await until(surface, s => !s.down && s.events.length > 1, 2000);
      results.push({ scenario, run, observed, hostCancelMillis, physicalReleaseMillis: cancelAt === undefined ? undefined : observed.events.at(-1)!.time - cancelAt });
      assert.equal(observed.dropped, 0);
      assert.equal(observed.events.filter(e => e.action === 0).length, 1, 'Continuation must not create extra DOWN events');
      assert.equal(observed.events.filter(e => e.action === 1).length, 1, 'Must receive exactly one UP');
      assert.equal(observed.events.filter(e => e.action === 3).length, 0, 'Expected continuous touch, not replacement gestures');
      assert.equal(new Set(observed.events.map(e => e.downTime)).size, 1);
      assert.ok(observed.events.every(e => e.pointers === 1));
      assert.equal(observed.events.at(-1)!.action, 1);
      if (scenario.cancelAfter !== undefined) {
        assert.equal(run.phase, 'cancelled', JSON.stringify(run));
        assert.ok(hostCancelMillis! < 1500, 'Cancellation should not wait for the remaining multi-second swipe');
        assert.ok(observed.events.at(-1)!.time - cancelAt! < 1000, 'Actual pointer release must follow cancellation');
        if (scenario.end !== startY) assert.ok(Math.abs(observed.events.at(-1)!.y - scenario.end) > Math.abs(startY - scenario.end) * .5);
      } else {
        assert.equal(run.phase, 'completed', JSON.stringify(run));
        assert.equal(run.result?.action?.result, true);
        assert.equal(run.result?.action?.shell, false, 'This check must exercise accessibility, not privileged injection');
        assert.ok(Math.abs(observed.events.at(-1)!.y - scenario.end) <= 1);
        assert.ok(observed.events.at(-1)!.time - observed.events[0]!.time >= scenario.duration - 30);
      }
    }
  } catch (error) { report.error = String(error); throw error; }
  finally {
    try {
      await json(`${base}/stop`, {});
      await until(() => json<{ engine: { activeRunId: number | null } }>('debug/engine'),
        s => s.engine.activeRunId === null, 8000, false);
    } catch (error) { report.cleanupError = String(error); throw error; }
    finally {
      try {
        const capture = await json<Capture>(`${base}/export`, {});
        report.diagnostics = capture;
        assert.equal(capture.systemRecording.sealed, true, 'Capture must include cancellation cleanup');
      } finally { await writeFile(output, JSON.stringify(report, null, 2)); }
    }
  }
  return { output, scenarios: results.length };
}

type JsonCall = <T>(path: string, body?: unknown) => Promise<T>;
type Session = { id: string; active: boolean; startedAt: number; systemFinalizing?: boolean };
type Engine = { engine: { activeRunId: number | null; cancelling: boolean } };
type Capture = { session: Session & { inputCheckpoint?: unknown }; events: { input?: unknown; inputDecision?: unknown }[]; systemRecording: { version: number; sealed: boolean; incomplete?: string | null;
  steps: { command?: { type: string; outcome?: { kind: string } }; ruleState?: unknown }[] };
  queryRecordings: unknown[]; droppedQueryRecordings?: number };
/** Session stop must cancel real input, seal its recording and leave the lane reusable. */
async function checkSessionLifetimes(json: JsonCall, until: DebugWait, surface: () => Promise<Surface>,
  position: { x: number; startY: number; endY: number; activity: string }, output: string, backend: GestureBackend, readiness: DebugReadiness,
  stopTransport?: () => Promise<void>) {
  await mkdir(dirname(output), { recursive: true });
  const results: Record<string, unknown>[] = [];
  const report: { backend: GestureBackend; readiness: DebugReadiness; results: typeof results; baseline?: unknown; error?: string } = { backend, readiness, results };
  const rule = '-99/2/li.songe.gkd.debug/1/1';
  const request = (requestId: string, duration: number) => ({ requestId, rule, execute: true,
    subscription: JSON.stringify({ id: -99, name: 'Session lifetime check', version: 1, apps: [{ id: 'li.songe.gkd.debug',
      groups: [{ key: 1, name: 'Session lifetime check', rules: [{ key: 1, matches: '[depth=0]', matchRoot: true,
        activityIds: [position.activity], action: 'swipe', actionCd: 0, swipeArg: {
          start: { x: String(position.x), y: String(position.startY) },
          end: { x: String(position.x), y: String(position.endY) }, duration,
        } }] }] }] }) });
  const idle = () => until(() => json<Engine>('debug/engine'), s => s.engine.activeRunId === null, 8000, false);
  const policy = await json<Record<string, unknown>>('debug/policy');
  try {
    // Prove the selected injector before testing cancellation, whose result is an exception rather than ActionResult.
    const probe = await json<Session>('debug/sessions', { });
    try {
      await json('debug/android/gesture-surface/reset', {});
      await json(`debug/sessions/${probe.id}/runs`, request('backend-probe', 250));
      const run = await waitForDebugRun(() => json<Run>(`debug/sessions/${probe.id}/runs/backend-probe`), until, 3000);
      report.baseline = { run, touch: await surface() };
      assert.equal(run.phase, 'completed', JSON.stringify(run));
      assert.equal(run.result?.action?.result, true);
      assert.equal(run.result?.action?.shell ?? false, backend !== 'accessibility', 'Input must use the requested backend');
    } finally { await json(`debug/sessions/${probe.id}/stop`, {}); await idle(); }
    for (const kind of stopTransport ? ['service-stop'] as const : ['stop'] as const) {
      await idle();
      await json('debug/android/gesture-surface/reset', {});
      const session = await json<Session>('debug/sessions', {
        detailed: true, ruleFilter: rule, recordSystem: true });
      const base = `debug/sessions/${session.id}`;
      const evidence: Record<string, unknown> = { kind, session };
      results.push(evidence);
      try {
        await json('debug/policy', { ...policy, rootWaitMillis: 321 });
        await json(`${base}/runs`, request(kind, 6000));
        await until(surface, s => s.down, 1500);
        await pause(350);
        evidence.beforeStop = await surface();
        if (kind === 'service-stop') await stopTransport!();
        else evidence.stopResponse = await json<Session>(`${base}/stop`, {});
        const observedAt = Date.now();
        const stoppedSession = await json<Session>(base);
        evidence.atStop = { session: stoppedSession, run: await json<Run>(`${base}/runs/${kind}`), engine: await json<Engine>('debug/engine'), touch: await surface() };
        assert.equal(stoppedSession.active, false, 'Stopping the service/session must end the diagnostic session');
        const run = await waitForDebugRun(() => json<Run>(`${base}/runs/${kind}`), until, 3000);
        const observed = await until(surface, s => !s.down && s.events.length > 1, 2000);
        evidence.run = run; evidence.touch = observed; evidence.hostCleanupMillis = Date.now() - observedAt;
        evidence.idle = await idle();
        assert.equal(run.phase, 'cancelled', JSON.stringify(run));
        assert.equal(observed.dropped, 0);
        assert.equal(observed.events.filter(e => e.action === 0).length, 1);
        const released = backend === 'accessibility' ? 1 : 3;
        assert.equal(observed.events.filter(e => e.action === 1).length, released === 1 ? 1 : 0);
        assert.equal(observed.events.filter(e => e.action === 3).length, released === 3 ? 1 : 0);
        assert.equal(new Set(observed.events.map(e => e.downTime)).size, 1);
        assert.equal(observed.events.at(-1)!.action, released);
        assert.ok(observed.events.at(-1)!.time - observed.events[0]!.time < 3500, 'Cancel must release before the six-second swipe completes');
        assert.ok(Math.abs(observed.events.at(-1)!.y - position.endY) > Math.abs(position.startY - position.endY) * .4);
        assert.deepEqual(await json('debug/policy'), policy, 'Stop restores session timing');
      } catch (error) { evidence.error = String(error); throw error; }
      finally {
        // Even a failed check retains the export and waits for physical cleanup before restoration.
        try { await json(`${base}/stop`, {}); await idle(); }
        finally {
          const capture = await json<Capture>(`${base}/export`, {});
          const capturePath = join(dirname(output), `${basename(output, '.json')}-${kind}-capture.json`);
          await writeFile(capturePath, JSON.stringify(capture, null, 2));
          evidence.capture = capturePath;
          assert.equal(capture.session.active, false);
          assert.equal(capture.systemRecording.version, 2);
          assert.equal(capture.systemRecording.sealed, true);
          assert.equal(capture.systemRecording.incomplete ?? null, null);
          assert.ok(capture.systemRecording.steps.some(s => s.ruleState));
          assert.ok(capture.systemRecording.steps.every(s => Boolean(s.command) !== Boolean(s.ruleState)));
          assert.equal(capture.queryRecordings.length, 1);
          const actions = capture.systemRecording.steps.filter(step => step.command?.type === 'action_end');
          assert.equal(actions.length, 1);
          assert.equal(actions[0]!.command!.outcome?.kind, 'cancelled');
          await writeFile(output, JSON.stringify(report, null, 2));
        }
      }
      // A replacement session must execute normally, even if an old client repeats Stop.
      await json('debug/android/gesture-surface/reset', {});
      const replacement = await json<Session>('debug/sessions', { recordSystem: true });
      const next = `debug/sessions/${replacement.id}`;
      try {
        try {
          await json(`${base}/stop`, {}); // Old export returns 400, but stop must not affect the replacement.
        } catch (error) { assert.match(String(error), /400|Unknown diagnostic session/); }
        assert.equal((await json<Session>(next)).active, true);
        await json(`${next}/runs`, request(`after-${kind}`, 250));
        const run = await waitForDebugRun(() => json<Run>(`${next}/runs/after-${kind}`), until, 3000);
        const touch = await surface();
        evidence.replacement = { run, touch };
        assert.equal(run.phase, 'completed', JSON.stringify(run));
        assert.equal(run.result?.action?.result, true);
        assert.equal(run.result?.action?.shell ?? false, backend !== 'accessibility');
        assert.equal(touch.down, false);
        assert.equal(touch.events.at(-1)!.action, 1);
        assert.ok(Math.abs(touch.events.at(-1)!.y - position.endY) <= 1);
      } finally { await json(`${next}/stop`, {}); await idle(); }
      console.log(`Passed ${backend}/${kind}: physical ${backend === 'accessibility' ? 'UP' : 'CANCEL'}, cancelled request, sealed diagnostic capture and replacement action.`);
    }
  } catch (error) { report.error = String(error); throw error; }
  finally { await writeFile(output, JSON.stringify(report, null, 2)); }
  return { output, scenarios: results.length, verified: true };
}
