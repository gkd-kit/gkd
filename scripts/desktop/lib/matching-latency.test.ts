import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { summarizeMatching } from '../commands/summarize-matching.ts';
import { matchingLatency, type LatencyTrace, type NodeProvenance } from './matching-latency.ts';

test('same-page updates and legacy observations cannot replace a missing confirmation', () => {
  for (const firstConfirmation of [false, undefined]) {
    const [page] = matchingLatency([{ sequence: 1, elapsedNanos: 100e6, runId: 0, reason: 'page_confirmed',
      pageObservation: { page: 1, appId: 'wechat', activityId: 'Login', firstConfirmation } }]);
    assert.equal(page!.foregroundConfirmedMillis, null);
    assert.equal(page!.confirmedToActionMillis, null);
  }
});

test('failed queries followed by a new root keep node provenance and separate waiting from execution', () => {
  const node: NodeProvenance = { readId: 1, connectionId: 2, service: 'A11y', source: 'Root', reason: 'Event',
    page: 3, appId: 'wechat', observedActivityId: 'Login', windowId: 4, acquiredAtMillis: 100, ageMillis: 0 };
  const trace = (sequence: number, ms: number, runId: number, reason: string, extra: Partial<LatencyTrace> = {}): LatencyTrace =>
    ({ sequence, elapsedNanos: ms * 1e6, runId, reason, ...extra });
  const events = [
    trace(1, 10, 0, 'arbitrary readable label', { serviceConnection: { event: 'Connected', service: 'A11y', connectionId: 2 } }),
    trace(2, 100, 0, 'node_acquired', { node }),
    trace(3, 400, 10, 'node_used', { nodeRole: 'Source', node: { ...node, ageMillis: 300 } }),
    trace(4, 410, 10, 'operation_finished', { operation: { name: 'Query', startedNanos: 400e6, durationNanos: 10e6, outcome: 'not_matched' } }),
    trace(5, 500, 0, 'node_acquired', { node: { ...node, readId: 5, acquiredAtMillis: 500 } }),
    trace(6, 505, 11, 'node_used', { nodeRole: 'Source', node: { ...node, readId: 5, acquiredAtMillis: 500, ageMillis: 5 } }),
    trace(7, 515, 11, 'operation_finished', { operation: { name: 'Query', startedNanos: 505e6, durationNanos: 10e6, outcome: 'matched' } }),
    trace(8, 515, 11, 'matched'), trace(9, 535, 11, 'action_succeeded'),
    trace(10, 540, 0, 'handoff', { serviceConnection: { event: 'HandoffShutdownRequested', service: 'A11y', connectionId: 2 } }),
  ];
  const [page] = matchingLatency(events);
  assert.equal(page!.queryToMatchMillis, 115);
  assert.equal(page!.queryExecutionMillis, 20);
  assert.equal(page!.notMatchedQueries, 1);
  assert.deepEqual(page!.nodeUses.map(node => node.readId), [1, 5]);
  assert.equal(page!.services[0]!.connectedMillis, 10);
  assert.equal(page!.observedToActionMillis, 435);
});

test('missing page or successful-action evidence stays unknown', () => {
  assert.deepEqual(matchingLatency([{ sequence: 1, elapsedNanos: 1, runId: 1, reason: 'matched' }]), []);
  const [page] = matchingLatency([{ sequence: 1, elapsedNanos: 1, runId: 1, reason: 'node_used', nodeRole: 'Source',
    node: { readId: 1, connectionId: 2, service: 'A11y', source: 'Root', reason: 'Event', page: 3,
      appId: 'wechat', observedActivityId: 'Login', windowId: 4, acquiredAtMillis: 100, ageMillis: 0 } }]);
  assert.equal(page!.actionSucceededMillis, null);
  assert.equal(page!.firstQueryMillis, null);
  assert.equal(page!.services[0]!.connectedMillis, null);
  assert.equal(page!.incompleteQueries.length, 1);
  assert.deepEqual(page!.incompleteQueries[0]!.nodeReads, [1]);
});

test('selector and fast-query evidence is emitted once, including queries without completion', () => {
  const node: NodeProvenance = { readId: 1, connectionId: 2, service: 'A11y', source: 'Root', reason: 'Event',
    page: 3, appId: 'wechat', observedActivityId: 'Login', windowId: 4, acquiredAtMillis: 100, ageMillis: 0 };
  const events: LatencyTrace[] = [
    { sequence: 1, elapsedNanos: 100, runId: 10, rule: 'finished', reason: 'read', node, nodeRole: 'Source' },
    { sequence: 2, elapsedNanos: 110, runId: 10, rule: 'finished', reason: 'matches[0]:miss' },
    { sequence: 3, elapsedNanos: 120, runId: 10, rule: 'finished', reason: 'search', fastQuery: { kind: 'Text', value: 'allow', count: 0 } },
    { sequence: 4, elapsedNanos: 130, runId: 10, rule: 'finished', reason: 'done', operation: { name: 'Query', startedNanos: 100, durationNanos: 30, outcome: 'not_matched' } },
    { sequence: 5, elapsedNanos: 140, runId: 10, rule: 'unfinished', reason: 'matches[1]:incomplete' },
    { sequence: 6, elapsedNanos: 150, runId: 10, rule: 'unfinished', reason: 'search', fastQuery: { kind: 'Text', value: 'allow', count: 1,
      anomalousCandidates: [{ className: 'TextView', viewId: 'login', contentDescription: 'allow', windowId: 4, visibleToUser: true }] } },
  ];
  const [page] = matchingLatency(events);
  assert.deepEqual(page!.queries[0]!.selectorOutcomes, ['matches[0]:miss']);
  assert.equal(page!.queries[0]!.fastQueries[0]!.count, 0);
  assert.deepEqual(page!.incompleteQueries[0]!.selectorOutcomes, ['matches[1]:incomplete']);
  assert.equal(page!.incompleteQueries[0]!.fastQueries[0]!.count, 1);
  assert.equal(page!.incompleteQueries[0]!.fastQueries[0]!.anomalousCandidates![0]!.contentDescription, 'allow');
  assert.equal(page!.incompleteQueries[0]!.outcome, null);
  assert.equal('selectorOutcomes' in page!, false);
  assert.equal('fastQueries' in page!, false);
});

test('export merges overlapping timelines once and preserves confirmation after detail eviction', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'gkd-latency-'));
  try {
    const input = join(directory, 'capture.json');
    const output = join(directory, 'summary.json');
    const origin = { readId: 1, connectionId: 2, service: 'A11y', source: 'Root', reason: 'Event', page: 3,
      appId: 'wechat', observedActivityId: 'Login', windowId: 4, acquiredAtMillis: 100, ageMillis: 0 };
    const events = [
      { sequence: 1, elapsedNanos: 0, runId: 0, stage: 'Root', reason: 'page_confirmed', pageObservation: { page: 3, appId: 'wechat', activityId: 'Login', firstConfirmation: true } },
      { sequence: 5, elapsedNanos: 100e6, runId: 10, stage: 'Root', reason: 'node_used', nodeRole: 'Source', node: origin },
      { sequence: 8, elapsedNanos: 110e6, runId: 10, stage: 'Idle', reason: 'operation_finished',
        operation: { id: 12, runId: 10, name: 'Query', startedNanos: 100e6, durationNanos: 10e6, outcome: 'matched' } },
      { sequence: 9, elapsedNanos: 110e6, runId: 10, stage: 'Selector', reason: 'matched' },
      { sequence: 10, elapsedNanos: 130e6, runId: 10, stage: 'Action', reason: 'action_succeeded' },
    ];
    const handoff = { sequence: 2, elapsedNanos: 10e6, runId: 0, stage: 'Idle', reason: 'handoff',
      serviceConnection: { event: 'HandoffShutdownRequested', service: 'A11y', connectionId: 2, targetConnectionId: 1 } };
    await writeFile(input, JSON.stringify({ session: { id: 'test', active: false, dropped: 7 }, events: events.slice(2), keyEvents: events.slice(1), droppedKeyEvents: 1, boundaryEvents: [events[0], handoff], droppedBoundaryEvents: 0, sizeLimitBytes: 20_000_000, omittedForSize: { queryRecordings: 2 } }));
    const summary = await summarizeMatching(input, output);
    assert.equal(summary.pageLatency[0]!.completedQueries, 1);
    assert.equal(summary.pageLatency[0]!.confirmedToActionMillis, 130);
    assert.equal(summary.evidenceCoverage.retention.boundaryEvents.count, 2);
    assert.equal(summary.evidenceCoverage.sizeLimitBytes, 20_000_000);
    assert.deepEqual(summary.evidenceCoverage.omittedForSize, { queryRecordings: 2 });
    assert.ok(summary.warnings.some(value => value.includes('文件体积预算')));
    assert.equal(summary.serviceTimeline[0]!.targetConnectionId, 1);
    assert.equal(summary.serviceTimeline[0]!.atMillis, 10);
    assert.equal(summary.evidenceCoverage.retention.keyEvents.firstElapsedMillis, 100);
    assert.equal(summary.evidenceCoverage.retention.events.firstElapsedMillis, 110);
    assert.equal(summary.evidenceCoverage.retention.events.memoryDropped, 7);
    assert.equal(summary.evidenceCoverage.retention.queryRecordings.exportDropped, 2);
    assert.equal(summary.evidenceCoverage.retention.events.firstAt, null);
    const saved = JSON.parse(await readFile(output, 'utf8'));
    assert.equal(saved.pageLatency[0].confirmedToActionMillis, 130);
    assert.deepEqual(saved.pageLatency[0].queries[0].nodeReads, [1]);
    const capture = JSON.parse(await readFile(input, 'utf8'));
    capture.files = { events: 'events.jsonl', keyEvents: 'key-events.jsonl', boundaryEvents: 'boundary-events.jsonl', queryRecordings: 'query-recordings.jsonl', systemRecording: 'system-recording.json' };
    for (const key of ['events', 'keyEvents', 'boundaryEvents', 'queryRecordings']) {
      await writeFile(join(directory, capture.files[key]), (capture[key] ?? []).map((event: unknown) => JSON.stringify(event)).join('\n'));
      delete capture[key];
    }
    await writeFile(join(directory, 'system-recording.json'), 'null');
    capture.files.accessibilitySettings = 'accessibility-settings.jsonl';
    const setting = { notifiedAt: 1, readAt: 2, trigger: 'changed', userId: 0, name: 'accessibility_enabled', value: '0', sourcePackage: 'android' };
    await writeFile(join(directory, 'accessibility-settings.jsonl'), JSON.stringify(setting) + '\n');
    await writeFile(input, JSON.stringify(capture));
    const splitSummary = await summarizeMatching(input, output);
    assert.deepEqual(splitSummary.accessibilitySettings, [setting]);
    assert.equal(splitSummary.evidenceCoverage.retention.accessibilitySettings.count, 1);
    assert.deepEqual(splitSummary.pageLatency, summary.pageLatency);
    assert.deepEqual(splitSummary.serviceTimeline, summary.serviceTimeline);

  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});


test('retention separates memory eviction from export trimming and accepts old captures', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'gkd-retention-'));
  try {
    const input = join(directory, 'capture.json');
    const output = join(directory, 'summary.json');
    const capture = {
      session: { id: 'retention', active: false, dropped: 7 },
      events: [
        { sequence: 8, time: 1200, elapsedNanos: 200e6, runId: 0, stage: 'Idle', reason: 'ready' },
        { sequence: 9, time: 1400, elapsedNanos: 400e6, runId: 0, stage: 'Idle', reason: 'ready' },
      ],
    };
    await writeFile(input, JSON.stringify({ ...capture, omittedForSize: { events: 2 } }));
    const current = await summarizeMatching(input, output);
    assert.deepEqual(current.evidenceCoverage.retention.events, {
      available: true, count: 2, memoryDropped: 5, exportDropped: 2, firstAt: 1200, lastAt: 1400,
      firstElapsedMillis: 200, lastElapsedMillis: 400,
    });
    await writeFile(input, JSON.stringify(capture));
    const old = await summarizeMatching(input, output);
    assert.equal(old.evidenceCoverage.retention.events.memoryDropped, 7);
    assert.equal(old.evidenceCoverage.retention.events.exportDropped, 0);
    assert.equal(old.evidenceCoverage.retention.queryRecordings.firstAt, null);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});


test('compact query sections restore plans and texts while distinguishing missing detail', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'gkd-compact-query-'));
  try {
    const input = join(directory, 'diagnostic.json');
    const output = join(directory, 'summary.json');
    const sharedText = 'repeated long value '.repeat(20);
    const capture: Record<string, any> = {
      formatVersion: 2,
      session: { id: 'compact', active: false, startedAt: 1000 },
      events: [], keyEvents: [], boundaryEvents: [], accessibilitySettings: [], systemRecording: null,
      droppedQueryRecordings: 1, droppedQuerySummaries: 0,
      queryPlans: [{ matches: ['[text]'] }],
      querySummaries: [
        { queryId: 1, runId: 1, rule: 'rule', planId: 0, startedAt: 1100, endedAt: 1200, durationNanos: 100e6, detailsRetained: false, outcome: 'not_matched' },
        { queryId: 2, runId: 2, rule: 'rule', planId: 0, startedAt: 1300, endedAt: 1900, durationNanos: 600e6, detailsRetained: true, retainedFor: 'slow_query', outcome: 'matched' },
      ],
      queryDetails: [{ queryId: 2, texts: [sharedText], operations: [], calls: [
        { operation: 'attr', arguments: [{ type: 'string', valueRef: 0 }], result: { type: 'string', valueRef: 0 }, durationNanos: 600e6 },
      ] }],
    };
    await writeFile(input, JSON.stringify(capture));
    const summary = await summarizeMatching(input, output);
    assert.equal(summary.queryRecordings.length, 2);
    assert.equal(summary.queryRecordings[0]!.callCount, null);
    assert.equal(summary.queryRecordings[0]!.detailsRetained, false);
    assert.deepEqual(summary.queryRecordings[1]!.plan, capture.queryPlans[0]);
    assert.deepEqual(summary.queryRecordings[1]!.calls[0]!.arguments, [{ type: 'string', value: sharedText }]);
    assert.equal(summary.queryRecordings[1]!.retainedFor, 'slow_query');
    assert.equal(summary.evidenceCoverage.retention.queryRecordings.count, 1);
    assert.equal(summary.evidenceCoverage.retention.querySummaries.count, 2);
    const files: Record<string, string> = {
      events: 'events.jsonl', keyEvents: 'key-events.jsonl', boundaryEvents: 'boundary-events.jsonl',
      accessibilitySettings: 'accessibility-settings.jsonl', querySummaries: 'query-summaries.jsonl',
      queryDetails: 'query-details.jsonl', queryPlans: 'query-plans.json', systemRecording: 'system-recording.json',
    };
    for (const [key, name] of Object.entries(files)) {
      await writeFile(join(directory, name), name.endsWith('.jsonl')
        ? capture[key].map((value: unknown) => JSON.stringify(value)).join('\n') : JSON.stringify(capture[key]));
      delete capture[key];
    }
    await writeFile(input, JSON.stringify({ ...capture, files }));
    const split = await summarizeMatching(input, output);
    assert.deepEqual(split.queryRecordings, summary.queryRecordings);
    await writeFile(join(directory, 'query-details.jsonl'), '');
    await assert.rejects(summarizeMatching(input, output), /Missing retained query detail/);
    await writeFile(join(directory, 'query-details.jsonl'), JSON.stringify({
      queryId: 2, texts: [], operations: [], calls: [{ result: { type: 'string', valueRef: 0 } }],
    }));
    await assert.rejects(summarizeMatching(input, output), /Invalid query text reference/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
