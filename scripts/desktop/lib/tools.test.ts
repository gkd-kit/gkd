import { assertDesktopTestPath, desktopTests, localRoot } from './local-paths.ts';
import { zipSync, strToU8 } from 'fflate';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, access, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { extractProfile, publishProfile } from '../commands/capture-profile.ts';
import { withAutomationPaused, automationRecoveryStatus, restoreAutomation, enableDebugAccessibility, type Adb } from './device-automation.ts';
import { createServer } from 'node:http';
import { matchingDebug } from '../commands/matching-debug.ts';
import { summarizeMatching } from '../commands/summarize-matching.ts';
import { withExistingDebugConnection } from './device-debug-connection.ts';
import { checkBinderTrace, type BinderTrace, type NodeBenchmark } from '../commands/summarize-binder.ts';
import { collectExpressionCorpus } from '../commands/expression-corpus.ts';

test('summary distinguishes cumulative timings from evicted detail and exposes retained time ranges', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'diagnostic-totals-'));
  try {
    const input = join(dir, 'capture.json');
    await writeFile(input, JSON.stringify({
      session: { id: 'session', active: false, startedAt: 1000, dropped: 90 },
      events: [{ sequence: 91, elapsedNanos: 500_000_000, runId: 0, stage: 'Idle', reason: 'tail' }],
      queryRecordings: [], droppedQueryRecordings: 40,
      entries: [{ source: 'source_read', reason: 'source_null', count: 123 }],
      operationStatistics: { completed: 100, omitted: 2, totals: [{ name: 'NodeRead:Root', rule: null, count: 98,
        totalDurationNanos: 980_000_000, slowest: { id: 1, runId: 1, durationNanos: 90_000_000 } }] },
      systemRecording: { startedAtMillis: 100, sealed: true, incomplete: 'system_recording_limit',
        steps: [{ sequence: 1, timeMillis: 120 }] },
    }));
    const summary = await summarizeMatching(input);
    assert.equal(summary.operationStatistics?.completed, 100);
    assert.equal(summary.operationStatistics?.totals[0]?.meanMillis, 10);
    assert.equal(summary.operationStatistics?.totals[0]?.slowest.durationMillis, 90);
    assert.equal(summary.coverage.events.firstElapsedMillis, 500);
    assert.equal(summary.coverage.system.lastElapsedMillis, 20);
    assert.equal(summary.coverage.queries.firstElapsedMillis, null);
    assert.equal(summary.entryTotals[0].count, 123);
    assert.ok(summary.warnings.some(w => w.includes('分组上限')));
    assert.equal(summary.operations.length, 0);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('diagnostic summary links node timings, input decisions and unfinished scheduling evidence without executing it', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'diagnostic-evidence-'));
  try {
    const input = join(dir, 'capture.json');
    const call = { operation: 'attr', arguments: [{ type: 'string', value: 'text' }],
      startedNanos: 2_000, durationNanos: 8_000_000, thread: 'worker', operationId: 12,
      failure: 'NodeFailure', failureMessage: 'disconnected' };
    await writeFile(input, JSON.stringify({
      session: { id: 'session', active: false, openOperations: [{ id: 13, name: 'NodeRead:Child', runId: 7 }],
        operationsAtStop: [{ id: 12, name: 'Selector.attr', runId: 7 }, { id: 13, name: 'NodeRead:Child', runId: 7 }] },
      appVersion: 'test-version', platform: 'test-platform', runtimeCapturedAt: 123,
      events: [
        { sequence: 1, elapsedNanos: 0, runId: 0, stage: 'Root', reason: 'screen_off',
          input: { input: { type: 'screen', interactive: false } }, inputDecision: { cancelWork: true } },
        { sequence: 2, elapsedNanos: 8_000_000, runId: 7, stage: 'Idle', reason: 'operation_finished_after_stop',
          operation: { id: 12, name: 'Selector.attr', runId: 7, parentOperationId: 11, durationNanos: 8_000_000 } },
      ],
      queryRecordings: [{ runId: 7, rule: 'rule', operationId: 11, plan: { matches: ['*'] },
        calls: [call], failure: 'NodeFailure', incomplete: 'recording_limit', durationNanos: 12_000_000, startedAt: 100, endedAt: 112, outcome: 'failed',
        operations: [{ id: 15, name: 'NodeRead:Text', runId: 7, durationNanos: 6_000_000 }] }],
      droppedQueryRecordings: 1,
      systemRecording: { sealed: false, steps: [{ sequence: 1, timeMillis: 5,
        command: { type: 'action_start', run: 7 }, effects: [] }], initial: {}, final: { active: { id: 7 } } },
    }));
    const summary = await summarizeMatching(input);
    assert.equal(summary.queryRecordings[0]?.calls[0]?.durationMillis, 8);
    assert.equal(summary.slowestNodeCalls[0]?.queryOperationId, 11);
    assert.equal(summary.slowestQueries[0]?.durationMillis, 12);
    assert.equal(summary.slowestQueries[0]?.outcome, 'failed');
    assert.ok(summary.operations.some(operation => operation.id === 15 && operation.durationMillis === 6));
    assert.deepEqual(summary.runDetails.find(run => run.runId === 7), { runId: 7, eventSequences: [2], queryIndexes: [0], systemSequences: [1], operationIds: [15, 12, 13] });
    assert.equal(summary.inputs.length, 1);
    assert.equal(summary.openOperations.length, 1);
    assert.equal(summary.operationsAtStop.length, 2);
    assert.equal(summary.completedAfterStop[0]?.id, 12);
    assert.equal(summary.appVersion, 'test-version');
    assert.equal(summary.runtimeCapturedAt, 123);
    assert.equal(summary.systemRecording?.timeline.length, 1);
    assert.equal(summary.systemRecording?.sealed, false);
    assert.ok(summary.warnings.some(w => w.includes('已保留当前现场')));
    assert.ok(summary.warnings.some(w => w.includes('节点访问记录不完整')));
    assert.equal(summary.counts.completed, 0);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('summary reconciles final requests and scheduler cleanup without treating a stopped runtime as current', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'diagnostic-final-state-'));
  try {
    const input = join(dir, 'capture.json');
    const capture = {
      session: { id: 'session', active: false, stoppedAt: 100, trackingComplete: true },
      runtime: { engine: { activeRunId: 7, attached: false, cancelling: true } }, runtimeCapturedAt: 100,
      events: [{ sequence: 1, elapsedNanos: 0, runId: 7, stage: 'Action', reason: 'action_started' }],
      runs: [{ request: { requestId: 'cancelled' }, engineRunId: 7, phase: 'cancelled' }],
      systemRecording: { sealed: true, finishingRun: 7, steps: [
        { sequence: 1, timeMillis: 20, command: { type: 'action_end', run: 7, outcome: { kind: 'cancelled' } }, effects: [] },
        { sequence: 2, timeMillis: 21, command: { type: 'finish', run: 8 }, effects: [] },
      ] },
    };
    await writeFile(input, JSON.stringify(capture));
    const summary = await summarizeMatching(input);
    assert.equal(summary.counts.unfinished, 0);
    assert.equal(summary.counts.cancelled, 1);
    assert.equal(summary.counts.completed, 0);
    assert.equal(summary.counts.finishedWithoutOutcome, 1);
    assert.equal(summary.runs.find(run => run.runId === 7)?.terminalSource, 'request');
    assert.equal(summary.runs.find(run => run.runId === 8)?.terminalSource, 'scheduler');
    assert.ok(summary.runs.every(run => run.durationMillis === null));
    assert.equal(summary.session.stoppedAt, 100);
    assert.equal(summary.actionOutcomes.length, 1);
    assert.ok(!summary.warnings.some(w => w.includes('手势回调')));
    // Once trace and scheduler evidence are unavailable, request-only runs still have an index entry.
    const requestOnly = { ...capture, events: [], systemRecording: null };
    await writeFile(input, JSON.stringify(requestOnly));
    const indexed = await summarizeMatching(input);
    assert.equal(indexed.runs[0]?.terminalSource, 'request');
    assert.deepEqual(indexed.runDetails, [{ runId: 7, eventSequences: [], queryIndexes: [], systemSequences: [], operationIds: [] }]);
    capture.runs = [];
    capture.systemRecording.finishingRun = 8;
    await writeFile(input, JSON.stringify(capture));
    const unrelated = await summarizeMatching(input);
    assert.equal(unrelated.counts.unfinished, 1);
    assert.ok(unrelated.warnings.some(w => w.includes('手势回调')), 'Another run finishing must not confirm run 7 cleanup');
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('expression corpus extracts click and swipe coordinates without retaining subscription metadata', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'expression-corpus-'));
  try {
    const input = join(dir, 'subscriptions');
    await mkdir(input);
    const source = JSON.stringify({ name: 'private subscription', apps: [{ id: 'private.app', groups: [{ rules: [
      { position: { x: 0, y: 'width/2' }, matches: '[text="private"]' },
      { swipeArg: { start: { left: 'width/2', top: null }, end: { x: 'screenWidth', y: 10 }, duration: 300 } },
    ] }] }] });
    const path = join(input, '1.json');
    await writeFile(path, source);
    const output = join(dir, 'corpus.json');
    const result = await collectExpressionCorpus(input, output);
    assert.equal(result.occurrences, 5);
    assert.deepEqual(JSON.parse(await readFile(output, 'utf8')), {
      files: 1, occurrences: 5, expressions: ['0', '10', 'screenWidth', 'width/2'],
    });
    assert.equal(await readFile(path, 'utf8'), source);
  } finally {
    assertDesktopTestPath(dir);
    await rm(dir, { recursive: true, force: true });
  }
});

test('Binder verification cross-checks measured node calls and refuses missing, truncated or lossy evidence', () => {
  const benchmark: NodeBenchmark = { samples: [{ cache: 'Cold', level: 'off', measurements: [
    { nodes: { reads: [{ operation: 'Root', calls: 2 }] } },
  ] }] };
  const trace: BinderTrace = { processes: 1, unfinishedSections: 0, unfinishedTransactions: 0, traceIssues: [], sections: [
    { name: 'GKD.benchmark.Cold.off.measured.root', sections: 2, binderTransactions: 2, onewayTransactions: 0, synchronousBinderMillis: .4 },
    { name: 'GKD.benchmark.Cold.off.measured.selector', sections: 1, binderTransactions: 1, onewayTransactions: 1, synchronousBinderMillis: 0 },
  ] };
  const complete = checkBinderTrace(trace, benchmark);
  assert.equal(complete.verified, true);
  assert.equal(complete.cases[0]!.transactionsPerQuery, 3);
  assert.equal(complete.cases[0]!.synchronousBinderMillis, .4);
  for (const damaged of [
    { ...trace, sections: trace.sections.slice(1) },
    { ...trace, sections: trace.sections.map(s => ({ ...s, sections: s.sections + 1 })) },
    { ...trace, processes: 2 }, { ...trace, unfinishedSections: 1 }, { ...trace, unfinishedTransactions: 1 },
    { ...trace, traceIssues: [{ name: 'ftrace_cpu_overrun_end', index: 0, value: 1 }] },
    { ...trace, sections: trace.sections.map(s => ({ ...s, binderTransactions: 0 })) },
  ]) assert.equal(checkBinderTrace(damaged, benchmark).verified, false);
  assert.equal(checkBinderTrace(trace, { samples: [] }).verified, false);
});

test('attach reuses a live transport, retries stale credentials and removes only its own forwards on failure', async () => {
  const token = 'a'.repeat(64);
  let credentialsReads = 0;
  let starts = 0;
  let forwards = 0;
  let removed = 0;
  const server = createServer((req, res) => {
    if (req.headers.authorization !== `Bearer ${token}`) { res.writeHead(401); res.end(); return; }
    assert.equal(req.url, '/health');
    res.end(JSON.stringify({ status: 'ready' }));
  });
  try {
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as { port: number }).port;
    const adb: Adb = async (...args) => {
      if (args[1]?.startsWith('pidof ')) return Buffer.from('1234');
      if (args[0] === 'forward') {
        if (args[1] === '--remove') { removed++; return Buffer.alloc(0); }
        forwards++; return Buffer.from(String(port));
      }
      if (args[1] === 'am' && args[2] === 'start-foreground-service') { starts++; return Buffer.alloc(0); }
      if (args[1] === 'run-as' && args[3] === 'cat') {
        credentialsReads++;
        return Buffer.from(JSON.stringify({ port: 8888, token: credentialsReads === 1 ? 'b'.repeat(64) : token }));
      }
      assert.fail(`Attach must not stop, install, launch a page, remove credentials or write settings: ${args.join(' ')}`);
    };
    const cancellation = new AbortController();
    const result = await withExistingDebugConnection(adb, cancellation.signal, async connection => {
      assert.equal(connection.pid, 1234); assert.equal(connection.token, token); return 'observed';
    });
    assert.equal(result, 'observed');
    await assert.rejects(withExistingDebugConnection(adb, cancellation.signal, async () => { throw new Error('read failed'); }), /read failed/);
    assert.equal(starts, 2, 'Idempotent start is allowed on each attach; shared transport is never stopped');
    assert.equal(forwards, 3); assert.equal(removed, 3);
  } finally {
    server.closeAllConnections();
    await new Promise<void>(resolve => server.close(() => resolve()));
  }
});

test('attach refuses absent or changed app processes without invoking the diagnostic operation', async () => {
  let started = false;
  let running = false;
  const adb: Adb = async (...args) => {
    if (args[1]?.startsWith('pidof ')) return Buffer.from(running ? started ? '5678' : '1234' : '');
    if (args[1] === 'am' && args[2] === 'start-foreground-service') { started = true; return Buffer.alloc(0); }
    assert.fail('No credential reads or forwarding after losing the original process');
  };
  const cancellation = new AbortController();
  const work = async () => assert.fail('Must not invoke diagnostic work');
  await assert.rejects(withExistingDebugConnection(adb, cancellation.signal, work), /No running/);
  assert.equal(started, false);
  running = true;
  await assert.rejects(withExistingDebugConnection(adb, cancellation.signal, work), /process changed/);
});

test('interrupted capture still stops and exports; stop failure preserves both errors and detailed filtering', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-diagnostic-test-'));
  const calls: string[] = [];
  const cancellation = new AbortController();
  let startBody: unknown;
  const server = createServer(async (req, res) => {
    calls.push(req.url!);
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(Buffer.from(chunk));
    if (req.url === '/debug/sessions') {
      startBody = JSON.parse(Buffer.concat(chunks).toString());
      res.end(JSON.stringify({ id: 'session' }));
    } else if (req.url?.endsWith('/stream')) {
      res.writeHead(200, { 'Content-Type': 'text/event-stream' });
      res.flushHeaders();
      cancellation.abort(new Error('interrupted'));
    } else if (req.url?.endsWith('/stop')) {
      res.writeHead(409); res.end(JSON.stringify({ error: 'stop failed' }));
    } else if (req.url?.endsWith('/export')) {
      res.end(JSON.stringify({ session: { id: 'session', active: true }, events: [] }));
    } else { res.writeHead(404); res.end(); }
  });
  try {
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as { port: number }).port;
    const output = join(dir, 'capture.json');
    await assert.rejects(matchingDebug({ port, duration: 1000, operation: 'capture', output,
      detailed: true, rule: '1/2/app.test/1/1', signal: cancellation.signal }), error => {
      assert.ok(error instanceof AggregateError);
      assert.equal(error.errors.length, 3);
      assert.match(String(error.errors[0]), /abort/i);
      assert.match(String(error.errors[1]), /stop failed/);
      assert.match(String(error.errors[2]), /404/);
      return true;
    });
    assert.deepEqual(calls, ['/debug/sessions', '/debug/sessions/session/stream', '/debug/sessions/session/stop', '/debug/sessions/session', '/debug/sessions/session/export']);
    assert.deepEqual(startBody, { ruleFilter: '1/2/app.test/1/1', detailed: true, recordQueries: false });
    assert.equal(JSON.parse(await readFile(output, 'utf8')).session.id, 'session');
    await assert.rejects(matchingDebug({ port, duration: 1000, operation: 'start', detailed: true }), /requires --rule/);
    assert.equal(calls.length, 5, 'Invalid detailed input must fail before creating a session');
  } finally {
    server.closeAllConnections();
    await new Promise<void>(resolve => server.close(() => resolve()));
    await rm(dir, { recursive: true, force: true });
  }
});

test('offline summary distinguishes incomplete traces, cancellation cleanup and match outcome', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-summary-test-'));
  const input = join(dir, 'capture.json');
  const event = (sequence: number, runId: number, millis: number, stage: string, reason: string) =>
    ({ sequence, runId, elapsedNanos: millis * 1_000_000, stage, reason });
  try {
    await writeFile(input, JSON.stringify({
      session: { id: 'partial', active: false, dropped: 2 },
      events: [event(3, 1, 50, 'Cancelled', 'cancelled'), event(4, 2, 100, 'Query', 'query_started'),
        event(5, 2, 200, 'Cancelling', 'foreground_changed'), event(6, 2, 320, 'Cancelled', 'cancelled'),
        event(7, 3, 400, 'Query', 'query_started'), event(8, 3, 420, 'Completed', 'work_completed'),
        event(9, 4, 450, 'Action', 'action_started')],
      runs: [{ request: { requestId: 'no-match', rule: 'rule' }, phase: 'completed', result: { matched: false, reason: 'not_matched' } },
        { request: { requestId: 'cleanup', rule: 'rule' }, phase: 'cancelling' }],
    }));
    const summary = await summarizeMatching(input, join(dir, 'summary.json'));
    assert.equal(summary.counts.cancelled, 2);
    assert.equal(summary.counts.completed, 1);
    assert.equal(summary.counts.unfinished, 1);
    assert.equal(summary.slowestRuns[0]?.durationMillis, 220);
    assert.equal(summary.slowestRuns[0]?.cancellationMillis, 120);
    assert.ok(summary.slowestRuns.every(run => run.runId !== 1), 'Missing start must not produce a duration');
    assert.deepEqual(summary.requests[0]?.result, { matched: false, reason: 'not_matched' });
    assert.ok(summary.warnings.some(w => w.includes('未采集全程调用统计')));
    assert.equal(summary.unfinishedRuns[0]?.durationMillis, null);
    await writeFile(input, JSON.stringify({ session: { id: 'conditions', active: false }, events: [
      { ...event(1, 1, 10, 'Selector', 'condition'), condition: { kind: 'comparison', matched: false,
        left: { type: 'string', value: 'actual' }, right: { type: 'string', value: 'expected' } } },
      { ...event(2, 1, 11, 'Selector', 'limit'), condition: { kind: 'limit_reached', matched: null } },
    ] }));
    const conditions = await summarizeMatching(input);
    assert.equal(conditions.recentConditions.length, 2);
    assert.ok(conditions.warnings.some(warning => warning.includes('记录上限')));
    assert.equal(conditions.counts.failed, 0, 'A false predicate is not proof that its enclosing rule/run failed');
    await writeFile(input, JSON.stringify({ session: { id: 'empty', active: false }, events: [] }));
    const empty = await summarizeMatching(input);
    assert.ok(empty.warnings.some(warning => warning.includes('没有采集到事件')));
    assert.equal(empty.evidenceCoverage.key.available, false);
    assert.deepEqual(empty.pageLatency, []);
    await writeFile(input, JSON.stringify({ session: { id: 'disconnected', active: false },
      events: [event(1, 1, 0, 'Action', 'action_started'), event(2, 1, 20, 'Cancelling', 'service_disconnected')],
      runtime: { engine: { attached: false, activeRunId: 1, cancelling: true } },
      systemRecording: { sealed: false, steps: [] } }));
    const disconnected = await summarizeMatching(input);
    assert.ok(disconnected.warnings.some(w => w.includes('可能不再交付手势回调')));
    assert.equal(disconnected.counts.completed, 0);
    assert.equal(disconnected.systemRecording?.sealed, false);
    await writeFile(input, JSON.stringify({ session: { id: 'operations', active: false,
      openOperations: [{ id: 1, name: 'Source', runId: 0 }], omittedOperations: 1 }, events: [
      { ...event(1, 0, 100, 'Idle', 'queued_behind_cancellation'), entrySource: 'coordinator', taskId: 8, generation: 4, blockingRunId: 17 },
      { ...event(2, 17, 5000, 'Idle', 'operation_finished'), operation: { id: 2, name: 'FindText', runId: 17, startedNanos: 0, durationNanos: 5_000_000_000 } },
      { ...event(3, 19, 5100, 'Query', 'query_started'), taskId: 8, generation: 4 },
      { ...event(4, 0, 5200, 'Idle', 'query_requested'), entrySource: 'coordinator', taskId: 9, generation: 4 },
    ] }));
    const operations = await summarizeMatching(input);
    assert.equal(operations.slowestOperations[0]?.durationMillis, 5000);
    assert.equal(operations.openOperations.length, 1);
    assert.equal(operations.omittedOperations, 1);
    assert.equal(operations.queueWaits[0]?.blockingRunId, 17);
    assert.equal(operations.queueWaits[0]?.waitMillis, 5000);
    assert.equal(operations.queueWaits[1]?.waitMillis, null, 'Missing start must not imply zero wait');
    assert.ok(operations.warnings.some(w => w.includes('尚未记录返回')));
    await writeFile(input, JSON.stringify({ session: { id: 'pressure', active: false }, events: [
      { ...event(1, 0, 100, 'Idle', 'event_queue_overflow'), eventDrop: { key: { appId: 'app' }, capacity: 64, ageMillis: 700 } },
      { ...event(2, 0, 200, 'Idle', 'node_read_capacity'), nodePressure: { activeReads: 4, capacity: 4, observedReads: 2, oldestObservedReadNanos: 600_000_000 } },
      event(3, 1, 300, 'Selector', 'query_descendant_limit'), event(4, 1, 301, 'Completed', 'work_completed'),
    ] }));
    const pressure = await summarizeMatching(input);
    assert.equal(pressure.eventDrops[0]?.eventDrop?.ageMillis, 700);
    assert.equal(pressure.nodePressure[0]?.nodePressure?.activeReads, 4);
    assert.equal(pressure.incompleteQueries.length, 1);
    assert.equal(pressure.warnings.length, 5);
    assert.ok(pressure.warnings.some(warning => warning.includes('没有独立关键时间线')));
    assert.equal(pressure.actionResults.length, 0);
    await writeFile(input, JSON.stringify({ samples: [] }));
    await assert.rejects(summarizeMatching(input), /diagnostic/);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('capture waits for owned cleanup before export and forwards the SSE resume cursor', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-settle-test-'));
  let polls = 0;
  let stopped = false;
  const server = createServer(async (req, res) => {
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(Buffer.from(chunk));
    if (req.url === '/debug/sessions') {
      assert.equal(JSON.parse(Buffer.concat(chunks).toString()).recordQueries, true);
      res.end('{"id":"session"}');
    } else if (req.url?.endsWith('/stream')) {
      assert.equal(req.headers['last-event-id'], '42');
      res.writeHead(200, { 'Content-Type': 'text/event-stream' }); res.flushHeaders();
    } else if (req.url?.endsWith('/stop')) {
      stopped = true; res.end('{}');
    } else if (req.url === '/debug/sessions/session') {
      assert.equal(stopped, true);
      polls++;
      res.end(JSON.stringify({ trackingComplete: polls >= 4 }));
    } else if (req.url?.endsWith('/export')) {
      assert.equal(polls, 4, 'Export also waits for physical reads after request cleanup');
      res.end('{"session":{"id":"session","active":false},"runs":[{"phase":"cancelled"}]}');
    } else { res.writeHead(404); res.end(); }
  });
  try {
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as { port: number }).port;
    const output = join(dir, 'capture.json');
    await matchingDebug({ port, duration: 50, operation: 'capture', rule: 'rule', recordQueries: true, after: '42', output });
    assert.equal(JSON.parse(await readFile(output, 'utf8')).runs[0].phase, 'cancelled');
    await assert.rejects(matchingDebug({ port, duration: 50, operation: 'start', recordQueries: true }), /requires --rule/);
  } finally {
    server.closeAllConnections();
    await new Promise<void>(resolve => server.close(() => resolve()));
    await rm(dir, { recursive: true, force: true });
  }
});

test('benchmark recovery survives disconnect and preserves other accessibility services and settings', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-benchmark-test-'));
  let store: Record<string, unknown> = { enableAutomator: false, automatorMode: 2, enableMatch: true };
  let staged: Record<string, unknown> | undefined;
  const secure: Record<string, string> = { enabled_accessibility_services: 'null', accessibility_enabled: '0' };
  let disconnected = false;
  const adb: Adb = async (...args) => {
    if (disconnected) throw new Error('offline');
    if (args[0] === 'get-serialno') return Buffer.from('benchmark-device');
    if (args[0] === 'push') staged = JSON.parse(await readFile(args[1]!, 'utf8'));
    if (args[1] === 'mv') store = staged!;
    if (args[1] === 'settings') {
      if (args[2] === 'get') return Buffer.from(secure[args[4]!] ?? 'null');
      secure[args[4]!] = args[2] === 'delete' ? 'null' : args[5]!;
    }
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(enableDebugAccessibility(adb), /Disable rule matching/);
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.equal(store.enableAutomator, false);
      assert.equal(store.automatorMode, 1);
      assert.equal(store.enableMatch, false);
      await enableDebugAccessibility(adb);
      assert.match(secure.enabled_accessibility_services!, /SelectToSpeakService/);
      // Simulate service connection and independent user edits before losing ADB.
      store.enableAutomator = true;
      store.theme = 'dark';
      secure.enabled_accessibility_services += ':other.app/OtherService';
      disconnected = true;
    }, dir, { accessibilityForDiagnostics: true }), /recovery required/);
    disconnected = false;
    assert.equal((await automationRecoveryStatus(adb, dir)).record?.saved.enableMatch, true);
    await restoreAutomation(adb, dir);
    assert.deepEqual({ ...store }, { enableAutomator: false, automatorMode: 2, enableMatch: true, theme: 'dark' });
    assert.deepEqual(secure, { enabled_accessibility_services: 'other.app/OtherService', accessibility_enabled: '1' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
    // When no unrelated service changes, restore the original global flag and absent setting.
    secure.enabled_accessibility_services = 'null'; secure.accessibility_enabled = '0';
    delete store.enableMatch;
    await withAutomationPaused(adb, dir, async () => { await enableDebugAccessibility(adb); }, dir,
      { accessibilityForDiagnostics: true });
    assert.deepEqual(secure, { enabled_accessibility_services: 'null', accessibility_enabled: '0' });
    assert.equal(Object.hasOwn(store, 'enableMatch'), false);
    // Actual Android 13 normalization must not be mistaken for another service being added.
    secure.enabled_accessibility_services = 'other.app/other.app.Service';
    secure.accessibility_enabled = '0';
    await withAutomationPaused(adb, dir, async () => {
      await enableDebugAccessibility(adb);
      secure.enabled_accessibility_services = secure.enabled_accessibility_services!.replace('other.app/other.app.Service', 'other.app/.Service');
    }, dir, { accessibilityForDiagnostics: true });
    assert.deepEqual(secure, { enabled_accessibility_services: 'other.app/.Service', accessibility_enabled: '0' });
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('automation diagnostics restores test app scope after disconnect without overwriting other scope edits', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-scope-test-'));
  const app = 'li.songe.gkd.debug';
  let store: Record<string, unknown> = { enableAutomator: true, automatorMode: 2, enableMatch: true, enableBlockA11yAppList: true };
  let scope = ['other.app', app];
  let staged = '', disconnected = false;
  const adb: Adb = async (...args) => {
    if (disconnected) throw new Error('offline');
    if (args[0] === 'get-serialno') return Buffer.from('scope-device');
    if (args[0] === 'push') staged = await readFile(args[1]!, 'utf8');
    if (args[1] === 'mv') {
      if (args.at(-1)!.endsWith('.txt')) scope = staged.split('\n').filter(Boolean);
      else store = JSON.parse(staged);
    }
    if (args[1] === 'settings' && args[2] === 'get') return Buffer.from(args[4] === 'accessibility_enabled' ? '0' : 'null');
    if (args[1]?.startsWith('if [ -f')) return Buffer.from(args[1].includes('a11y_scope_app_list') ? scope.join('\n') : JSON.stringify(store));
    return Buffer.from('');
  };
  const options = { accessibilityForDiagnostics: true, automationForDiagnostics: true };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.deepEqual(scope, ['other.app']);
      assert.equal(store.enableMatch, false);
      assert.equal(store.enableBlockA11yAppList, false);
      scope.push('new.app');
      store.theme = 'dark';
      disconnected = true;
    }, dir, options), /recovery required/);
    disconnected = false;
    assert.equal((await automationRecoveryStatus(adb, dir)).record?.restoreTestAppA11yScope, true);
    await restoreAutomation(adb, dir);
    assert.deepEqual(scope, ['other.app', 'new.app', app]);
    assert.deepEqual(store, { enableAutomator: true, automatorMode: 2, enableMatch: true, enableBlockA11yAppList: true, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
    // Absent scope membership and absent settings must remain absent after diagnostics.
    scope = ['other.app'];
    Reflect.deleteProperty(store, 'enableBlockA11yAppList');
    await withAutomationPaused(adb, dir, async () => {
      assert.equal((await automationRecoveryStatus(adb, dir)).record?.restoreTestAppA11yScope, undefined);
      assert.equal(store.enableBlockA11yAppList, false);
    }, dir, options);
    assert.deepEqual(scope, ['other.app']);
    assert.equal(Object.hasOwn(store, 'enableBlockA11yAppList'), false);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('capture restores only automation fields on failure, preserving intervening settings', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-capture-test-'));
  let store: Record<string, unknown> = { enableAutomator: true, automatorMode: 2, theme: 'light' };
  let pending: Record<string, unknown> | undefined;
  const adb: Adb = async (...args) => {
    if (args[0] === 'get-serialno') return Buffer.from('test-device');
    if (args[0] === 'push') pending = JSON.parse(await readFile(args[1]!, 'utf8'));
    if (args[1] === 'mv') store = pending!;
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => {
      assert.equal(store.enableAutomator, false); assert.equal(store.automatorMode, 1);
      store.theme = 'dark'; throw new Error('export failed');
    }, dir), /export failed/);
    assert.deepEqual(store, { enableAutomator: true, automatorMode: 2, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('capture retains recovery record when restoration fails and refuses another run', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-capture-test-'));
  let store: Record<string, unknown> = { enableAutomator: true };
  let staged: Record<string, unknown> | undefined;
  let failRestore = false;
  const adb: Adb = async (...args) => {
    if (args[0] === 'get-serialno') return Buffer.from('test-device');
    if (args[0] === 'push') {
      if (failRestore) throw new Error('device disconnected');
      staged = JSON.parse(await readFile(args[1]!, 'utf8'));
    }
    if (args[1] === 'mv') store = staged!;
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => { failRestore = true; }, dir), /recovery required/);
    const { path, record } = await automationRecoveryStatus(adb, dir);
    assert.deepEqual(record?.saved, { enableAutomator: true });
    // A different capture directory must still find the unfinished record for this device.
    await assert.rejects(withAutomationPaused(adb, join(dir, 'new-capture'), async () => assert.fail('Must not run'), dir), /recovery required/);
    assert.deepEqual(JSON.parse(await readFile(path, 'utf8')), record);
    failRestore = false;
    store.theme = 'changed while disconnected';
    assert.equal((await restoreAutomation(adb, dir)).restored, true);
    assert.deepEqual(store, { enableAutomator: true, theme: 'changed while disconnected' });
    assert.equal((await restoreAutomation(adb, dir)).restored, false);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('test path guard accepts session files and rejects daily data and sibling prefixes', () => {
  assert.doesNotThrow(() => assertDesktopTestPath(join(desktopTests, 'session', 'data', 'backup.zip')));
  for (const path of [join(localRoot, 'desktop', 'data'), desktopTests,
    join(desktopTests, '..', 'android', 'session'), `${desktopTests}-other/session`]) {
    assert.throws(() => assertDesktopTestPath(path), /isolated Desktop test path/);
  }
});

test('recovery validates device identity before writes and retains record until verified', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-recovery-test-'));
  let store: Record<string, unknown> = { enableAutomator: false, automatorMode: 2, theme: 'light' };
  let disconnected = false, ignoreWrites = false, writes = 0;
  let staged: Record<string, unknown> | undefined;
  const adb: Adb = async (...args) => {
    if (disconnected) throw new Error('offline');
    if (args[0] === 'get-serialno') return Buffer.from('device-a');
    if (args[0] === 'push') {
      writes++;
      if (!ignoreWrites) staged = JSON.parse(await readFile(args[1]!, 'utf8'));
    }
    if (args[1] === 'mv' && !ignoreWrites) store = staged!;
    return Buffer.from(args[1]?.startsWith('if [ -f') ? JSON.stringify(store) : '');
  };
  try {
    await assert.rejects(withAutomationPaused(adb, dir, async () => { disconnected = true; }, dir), /recovery required/);
    disconnected = false;
    const pending = await automationRecoveryStatus(adb, dir);
    await writeFile(pending.path, JSON.stringify({ ...pending.record, serial: 'device-b' }));
    const before = writes;
    await assert.rejects(restoreAutomation(adb, dir), /identity mismatch/);
    assert.equal(writes, before);
    // Diagnostic report location is not required to recover device settings.
    await writeFile(pending.path, JSON.stringify({ ...pending.record, captureDirectory: undefined }));
    ignoreWrites = true;
    await assert.rejects(restoreAutomation(adb, dir), /Restore mismatch/);
    await access(pending.path);
    ignoreWrites = false;
    store.theme = 'dark';
    await restoreAutomation(adb, dir);
    assert.deepEqual(store, { enableAutomator: false, automatorMode: 2, theme: 'dark' });
    assert.equal((await automationRecoveryStatus(adb, dir)).record, null);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('profile extraction imports device data and rejects traversal', async () => {
  await mkdir(desktopTests, { recursive: true });
  const root = await mkdtemp(join(desktopTests, 'profile-'));
  const files = {
    'profile.json': strToU8(JSON.stringify({ width: 393, height: 852, fontScale: 1 })),
    'apps.json': strToU8('{"apps":[]}'), 'settings.json': strToU8('{}'),
    'seed.json': strToU8('{"blocked":["example.app"],"subscriptions":{"42":false}}'),
    'subscriptions/42.json': strToU8('{"id":42,"name":"fixture","version":1}'),
  };
  try {
    await assert.rejects(extractProfile(zipSync({ ...files, '../escape.json': strToU8('{}') }), root), /Unexpected/);
    await extractProfile(zipSync(files), root);
    assert.equal(JSON.parse(await readFile(join(root, 'seed.json'), 'utf8')).subscriptions['42'], false);
    assert.equal(JSON.parse(await readFile(join(root, 'subscriptions/42.json'), 'utf8')).name, 'fixture');
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('profile publication restores previous profile on failure and removes stale files on success', async () => {
  await mkdir(desktopTests, { recursive: true });
  const root = await mkdtemp(join(desktopTests, 'profile-publish-'));
  const output = join(root, 'profile'), staged = join(root, 'staged');
  try {
    await mkdir(output); await writeFile(join(output, 'old.json'), 'original');
    await assert.rejects(publishProfile(staged, output));
    assert.equal(await readFile(join(output, 'old.json'), 'utf8'), 'original');
    await mkdir(staged); await writeFile(join(staged, 'profile.json'), 'new');
    await publishProfile(staged, output);
    assert.equal(await readFile(join(output, 'profile.json'), 'utf8'), 'new');
    await assert.rejects(access(join(output, 'old.json')));
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('system capture waits for ordinary engine cleanup even with no manual debug runs', async () => {
  await mkdir(desktopTests, { recursive: true });
  const dir = await mkdtemp(join(desktopTests, 'gkd-system-capture-'));
  let polls = 0;
  const server = createServer(async (req, res) => {
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(Buffer.from(chunk));
    if (req.url === '/debug/sessions') {
      assert.equal(JSON.parse(Buffer.concat(chunks).toString()).recordSystem, true);
      res.end('{"id":"system"}');
    } else if (req.url?.endsWith('/stream')) {
      res.writeHead(200, { 'Content-Type': 'text/event-stream' }); res.end();
    } else if (req.url?.endsWith('/stop')) res.end('{}');
    else if (req.url === '/debug/sessions/system') {
      const finalizing = ++polls < 3;
      res.end(JSON.stringify({ systemFinalizing: finalizing, trackingComplete: !finalizing }));
    }
    else if (req.url?.endsWith('/export')) {
      assert.equal(polls, 3);
      res.end('{"session":{"id":"system","active":false},"systemRecording":{"sealed":true}}');
    } else { res.writeHead(404); res.end(); }
  });
  try {
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = (server.address() as { port: number }).port;
    const output = join(dir, 'capture.json');
    await matchingDebug({ port, duration: 50, operation: 'capture', recordSystem: true, output });
    assert.equal(JSON.parse(await readFile(output, 'utf8')).systemRecording.sealed, true);
  } finally {
    server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve()));
    await rm(dir, { recursive: true, force: true });
  }
});
