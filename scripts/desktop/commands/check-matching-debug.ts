import assert from 'node:assert/strict';
import { setTimeout as pause } from 'node:timers/promises';
import { DesktopClient } from '../lib/desktop-client.ts';
import { matchingDebug } from './matching-debug.ts';

/** Runs only against --test Desktop; verifies real shared matching and HTTP request ownership. */
export async function checkMatchingDebug(client: DesktopClient) {
  const json: (path: string, body?: unknown) => Promise<any> = client.json.bind(client);
  assert.equal((await json('desktop/state')).isolated, true, 'An isolated Desktop is required');
  const readiness = await json('debug/readiness');
  assert.equal(readiness.checks.observe.ready, true);
  assert.equal(readiness.checks.startCapture.ready, true);
  assert.deepEqual(readiness.checks.liveQuery.reasons, ['live_nodes_unsupported']);
  const originalPolicy = await json('debug/policy');
  assert.equal((await client.response('debug/policy', originalPolicy)).status, 409, 'Timing edits require a diagnostic session');
  const session = await json('debug/sessions', { detailed: true, recordSystem: true, ruleFilter: '1/2/app.test/1/1' });
  const path = `debug/sessions/${session.id}`;
  let latestSessionId = session.id;
  const observing = await json('debug/readiness');
  assert.equal(observing.session.id, session.id);
  assert.equal(observing.checks.observe.ready, true);
  assert.deepEqual(observing.checks.startCapture.reasons, ['diagnostic_session_active']);
  const request = {
    requestId: 'match-only',
    rule: '1/2/app.test/1/1',
    subscription: JSON.stringify({ id: 1, name: 'Diagnostic test', version: 1,
      apps: [{ id: 'app.test', groups: [{ key: 1, name: 'Test', rules: [{ key: 1, matches: '[text="target"][depth=1]' }] }] }] }),
    simulation: {
      app: { id: 'app.test', name: 'Test', versionCode: 1, versionName: '1', isSystem: false, mtime: 0, hidden: false, userId: 0 },
      root: { name: 'Root', attributes: { enabled: true, visibleToUser: true }, children: [{ name: 'Button', attributes: { text: 'target' } }] },
      actionMillis: 10_000, cleanupMillis: 500,
    },
  };
  async function terminal(id: string) {
    for (let i = 0; i < 100; i++) {
      const result = await json(`${path}/runs/${id}`);
      if (['completed', 'cancelled', 'failed'].includes(result.phase)) return result;
      await pause(50);
    }
    throw new Error(`Run ${id} did not stop`);
  }
  try {
    const beforeBenchmark = await json(path);
    const benchmark = await json('debug/benchmark', {
      request: { ...request, simulation: { ...request.simulation, actionMillis: 0, cleanupMillis: 0 } },
      iterations: 5, warmup: 2,
    });
    assert.equal(benchmark.samples.length, 3);
    assert.ok(benchmark.samples.every((sample: { matched: boolean }) => sample.matched));
    assert.equal(new Set(benchmark.samples.map((sample: { nodeAccessesPerQuery: number }) => sample.nodeAccessesPerQuery)).size, 1);
    assert.deepEqual(await json(path), beforeBenchmark, 'Benchmark must not mutate the active diagnostic session');
    assert.equal((await client.response('debug/benchmark', { request: { ...request, execute: true } })).status, 400);
    const policy = { ...originalPolicy, rootWaitMillis: 123 };
    await json('debug/policy', policy);
    assert.deepEqual((await json('debug/engine')).engine.policy, policy);
    await json(`${path}/runs`, request);
    const matched = await terminal(request.requestId);
    assert.equal(matched.result?.reason, 'matched_without_execution', JSON.stringify(matched));
    assert.equal(matched.result?.simulation, true);
    assert.deepEqual(await json(`${path}/runs`, request), matched, 'Duplicate request must return the same result');
    assert.deepEqual(await json(`${path}/runs`, { ...request, simulation: { ...request.simulation,
      root: { ...request.simulation.root, attributes: { visibleToUser: true, enabled: true } },
    } }), matched, 'JSON object field order must not change request identity');
    assert.equal((await client.response(`${path}/runs`, { ...request, simulation: { ...request.simulation,
      app: { ...request.simulation.app, versionName: 'changed' },
    } })).status, 400, 'Request identity must compare every app field, not AppInfo.equals');
    const conflict = await client.response(`${path}/runs`, { ...request, execute: true });
    assert.equal(conflict.status, 400);
    await json(`${path}/runs`, { ...request, requestId: 'cancel-input', execute: true });
    for (let i = 0; i < 100; i++) {
      if ((await json(path)).stage === 'Action') break;
      await pause(20);
      if (i === 99) throw new Error('Simulated action did not start');
    }
    const started = Date.now();
    await json(`${path}/runs/cancel-input/cancel`, {});
    const cancelling = await json(`${path}/runs/cancel-input`);
    assert.equal(cancelling.phase, 'cancelling');
    const cancelled = await terminal('cancel-input');
    assert.equal(cancelled.phase, 'cancelled');
    assert.ok(Date.now() - started >= 400, 'Cancellation must wait for simulated physical cleanup');
    const events = await json(`${path}/events?after=0`);
    assert.ok(events.events.some((event: { reason: string }) => event.reason === 'matches[0]:hit'));
    const exported = await json(`${path}/export`, {});
    assert.equal(exported.runs.length, 2);
    assert.ok(Array.isArray(exported.ruleSnapshots) && Array.isArray(exported.entries));
    assert.ok(exported.scope.includes('observed_evidence_only'));
    assert.equal(exported.queryRecordings.length, 2);
    assert.equal(exported.queryRecordings[0].runId, matched.engineRunId, 'Requests must link to engine events and recordings');
    for (const recording of exported.queryRecordings) {
      assert.equal(recording.failure, null);
      assert.ok(recording.operationId > 0);
      assert.ok(recording.calls.every((call: { durationNanos: number; startedNanos: number }) => call.durationNanos >= 0 && call.startedNanos >= 0));
    }
    assert.deepEqual(exported.runtime.engine.policy, policy);
    assert.ok(exported.events.some((event: { reason: string; policy?: { rootWaitMillis: number } }) =>
      event.reason === 'query_started' && event.policy?.rootWaitMillis === 123));
    assert.ok(exported.events.some((event: { condition?: { kind: string; matched: boolean; left?: { value: string }; right?: { value: string } } }) =>
      event.condition?.kind === 'comparison' && event.condition.matched &&
      event.condition.left?.value === 'target' && event.condition.right?.value === 'target'));
    for (const [index, rule] of [
      { matches: '[text="missing" && clickable=true]' },
      { anyMatches: ['[text="missing"]', '[text="target"]'] },
      { matches: '[text="target"]', excludeMatches: '[text="target"]' },
      { matches: '[text="target"]', excludeAllMatches: '[text="target"]' },
    ].entries()) {
      const diagnosticRequest = { ...request, requestId: `condition-${index}`,
        subscription: JSON.stringify({ id: 1, name: 'Diagnostic test', version: 1,
          apps: [{ id: 'app.test', groups: [{ key: 1, name: 'Test', rules: [{ key: 1, ...rule }] }] }] }) };
      await json(`${path}/runs`, diagnosticRequest);
      const state = await terminal(diagnosticRequest.requestId);
      assert.equal(state.phase, 'completed', JSON.stringify(state));
      assert.equal(state.result.matched, index === 1, JSON.stringify(state));
    }
    const explained = await json(`${path}/export`, {});
    const conditions = explained.events.flatMap((event: { condition?: Record<string, unknown> }) => event.condition ? [event.condition] : []);
    for (const part of ['matches[0]', 'anyMatches[0]', 'anyMatches[1]', 'excludeMatches[0]', 'excludeAllMatches[0]']) {
      assert.ok(conditions.some((condition: { selectorPart: string }) => condition.selectorPart === part), part);
    }
    assert.ok(conditions.some((condition: { kind: string; selector: string; matched: unknown }) =>
      condition.kind === 'skipped' && condition.selector === '[text="missing" && clickable=true]' && condition.matched === null));
    await json(`${path}/runs`, { ...request, requestId: 'action-result', execute: true,
      simulation: { ...request.simulation, actionMillis: 0, cleanupMillis: 0 } });
    assert.equal((await terminal('action-result')).result.action.result, true);
    const withAction = await json(`${path}/export`, {});
    assert.ok(withAction.events.some((event: { action?: { result: boolean; shell: boolean } }) => event.action?.result === true && !event.action.shell));
    const cancellation = new AbortController();
    const timer = setTimeout(() => cancellation.abort(), 100);
    try {
      await assert.rejects(matchingDebug({ port: client.port, operation: 'watch', session: session.id,
        duration: 2000, signal: cancellation.signal }), { name: 'AbortError' });
    } finally { clearTimeout(timer); }
    await json(`${path}/runs`, { ...request, requestId: 'session-stop', execute: true });
    for (let i = 0; i < 100; i++) {
      if ((await json(path)).stage === 'Action') break;
      await pause(20);
      if (i === 99) throw new Error('Session-owned action did not start');
    }
    const sessionStoppedAt = Date.now();
    const stream = client.response(`${path}/stream`, undefined, { timeoutMillis: 10_000 });
    await json(`${path}/stop`, {});
    assert.equal((await json(`${path}/runs/session-stop`)).phase, 'cancelling');
    assert.equal((await client.response('debug/sessions', { })).status, 409,
      'A replacement session must wait for the old request to publish its terminal state');
    assert.equal((await terminal('session-stop')).phase, 'cancelled');
    assert.ok(Date.now() - sessionStoppedAt >= 400, 'Session stop must retain the request until action cleanup');
    const capture = await json(`${path}/export`, {});
    const streamed = await stream;
    assert.equal(streamed.status, 200);
    const pages = streamed.body.toString().split('\n').filter(line => line.startsWith('data: '))
      .map(line => JSON.parse(line.slice(6)));
    assert.equal(pages.at(-1).trackingComplete, true);
    assert.equal(pages.at(-1).hasMore, false);
    assert.ok(pages.some(page => page.events.some((event: any) => event.reason === 'operation_finished_after_stop')));
    assert.ok(pages.flatMap(page => page.events).every(event => 'blockingRunId' in event), 'SSE retains default fields like JSON export');
    assert.ok(!("initialConfiguration" in capture));
    assert.ok(capture.ruleDefinitions);
    assert.equal(capture.session.trackingComplete, true);
    assert.ok(capture.runtimeCapturedAt > 0);
    assert.ok(capture.appVersion);
    assert.ok(capture.session.operationsAtStop.length > 0);
    assert.equal(capture.session.openOperations.length, 0);
    assert.ok(capture.events.some((event: { reason: string }) => event.reason === 'operation_finished_after_stop'));
    assert.equal(capture.runs.find((run: any) => run.request.requestId === 'session-stop').phase, 'cancelled');
    assert.equal(capture.systemRecording.version, 2);
    assert.equal(capture.systemRecording.sealed, true);
    const steps = capture.systemRecording.steps;
    const actions = steps.filter((step: any) => step.command?.type === 'action_end');
    assert.equal(actions.length, 3);
    assert.equal(actions[0].command.outcome.kind, 'cancelled');
    const cancel = steps.find((step: any) => step.effects.some((effect: any) => effect.type === 'cancel'));
    assert.ok(actions[0].timeMillis - cancel.timeMillis >= 400);
    assert.equal(actions[1].command.outcome.result.result, true);
    assert.equal(actions[2].command.outcome.kind, 'cancelled');
    assert.equal(capture.queryRecordings.length, 8);
    const next = await json('debug/sessions', { });
    latestSessionId = next.id;
    const nextPath = `debug/sessions/${next.id}`;
    // Reuse an ID with a different result: the new request cache must not replace historical reads.
    await json(`${nextPath}/runs`, { ...request, requestId: 'session-stop', execute: false });
    for (let i = 0; i < 100; i++) {
      const state = await json(`${nextPath}/runs/session-stop`);
      if (state.phase === 'completed') break;
      if (i === 99) throw new Error('New session request did not complete');
      await pause(50);
    }
    assert.deepEqual(await json(`${path}/runs`), capture.runs);
    assert.deepEqual(await json(`${path}/runs/session-stop`), capture.runs.find((run: any) => run.request.requestId === 'session-stop'));
    assert.deepEqual((await json(`${path}/export`, {})).runs, capture.runs);
    assert.equal((await json(`${nextPath}/runs/session-stop`)).phase, 'completed');
    return { matched: true, duplicateProtected: true, cancellationWaitedForCleanup: true, detailedTrace: true,
      attributeExplanation: true, allSelectorGroups: true, timingSnapshot: true, queryEvidence: true, actionResultExport: true,
      systemEvidence: true, actionLifecycleEvidence: true, sessionStopWaitedForCleanup: true, historicalRequests: true };
  } finally {
    await json(`${path}/stop`, {});
    if (latestSessionId !== session.id) await json(`debug/sessions/${latestSessionId}/stop`, {});
    assert.deepEqual(await json('debug/policy'), originalPolicy, 'Stopping diagnostics restores timing');
    const stopped = await json('debug/readiness');
    assert.equal(stopped.session.id, latestSessionId, 'Stopped sessions remain discoverable for export');
    assert.equal(stopped.session.active, false);
    assert.equal(stopped.checks.startCapture.ready, true);
  }
}
