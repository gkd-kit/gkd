import { readFile, writeFile } from 'node:fs/promises';
import { matchingLatency, type LatencyTrace } from '../lib/matching-latency.ts';

type Trace = LatencyTrace & {
  stage: string;
  condition?: { kind: string; matched: boolean | null };
  action?: { action: string; result: boolean; shell?: boolean; position?: unknown };
  operation?: { id: number; name: string; runId: number; startedNanos: number; durationNanos?: number | null; outcome?: string | null };
  taskId?: number | null; generation?: number | null; blockingRunId?: number | null;
  eventDrop?: { key: unknown; capacity: number; ageMillis: number } | null;
  nodePressure?: { activeReads: number; capacity: number; observedReads: number; oldestObservedReadNanos: number | null } | null;
  input?: unknown; inputDecision?: unknown;
};

function object(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('Expected a diagnostic JSON object');
  return value as Record<string, unknown>;
}

/** Reads the shared diagnostic capture. Never interprets completion as a successful match. */
export async function summarizeMatching(input: string, output?: string) {
  const capture = object(JSON.parse(await readFile(input, 'utf8')));
  const session = object(capture.session);
  if (typeof session.id !== 'string' || typeof session.active !== 'boolean' || !Array.isArray(capture.events)) {
    throw new Error('Expected a matching diagnostic export with session and events');
  }
  const decodeTraces = (values: unknown[]): Trace[] => values.map(value => {
    const event = object(value);
    for (const key of ['sequence', 'elapsedNanos', 'runId']) {
      if (typeof event[key] !== 'number' || !Number.isFinite(event[key]) || event[key] < 0) throw new Error(`Invalid event ${key}`);
    }
    if (typeof event.stage !== 'string' || typeof event.reason !== 'string' ||
        (event.rule != null && typeof event.rule !== 'string')) throw new Error('Invalid event stage, reason or rule');
    return event as Trace;
  });
  const detailEvents = decodeTraces(capture.events);
  const keyEvents = decodeTraces(Array.isArray(capture.keyEvents) ? capture.keyEvents : []);
  const boundaryEvents = decodeTraces(Array.isArray(capture.boundaryEvents) ? capture.boundaryEvents : []);
  const events = [...new Map([...detailEvents, ...keyEvents, ...boundaryEvents].map(event => [event.sequence, event])).values()]
    .sort((a, b) => a.sequence - b.sequence);
  const warnings: string[] = [];
  const gaps = events.filter((event, index) => event.sequence !== (events[index - 1]?.sequence ?? 0) + 1).length;
  if (session.active) warnings.push('采集仍在进行，导出不是最终结果。');
  if (session.dropped || gaps) warnings.push('事件不完整：存在覆盖或序号缺口，不能据此断言某个阶段从未发生。');
  if (capture.droppedRuleSnapshots) warnings.push('部分规则快照已被覆盖。');
  if (!Array.isArray(capture.keyEvents)) warnings.push('此记录没有独立关键时间线或节点来源，缺失的延迟边界保持未知。');
  if (capture.droppedKeyEvents) warnings.push('关键时间线也达到容量上限；摘要中的首次时间仅指保留下来的首次观测。');
  if (!Array.isArray(capture.boundaryEvents)) warnings.push('此记录没有独立服务交接时间线。');
  if (capture.droppedBoundaryEvents) warnings.push('服务交接时间线达到容量上限，早期边界可能缺失。');
  const recordings = (Array.isArray(capture.queryRecordings) ? capture.queryRecordings : []).map(value => {
    const recording = object(value);
    const calls = (Array.isArray(recording.calls) ? recording.calls : []).map((value, index) => {
      const call = object(value);
      return { ...call, operation: call.operation, arguments: call.arguments, index, durationMillis: typeof call.durationNanos === 'number' ? call.durationNanos / 1_000_000 : null };
    });
    return { runId: recording.runId, rule: recording.rule, operationId: recording.operationId ?? null,
      plan: recording.plan, source: recording.source, result: recording.result,
      startedAt: recording.startedAt ?? null, endedAt: recording.endedAt ?? null,
      durationMillis: typeof recording.durationNanos === 'number' ? recording.durationNanos / 1_000_000 : null,
      outcome: recording.outcome ?? null,
      operations: (Array.isArray(recording.operations) ? recording.operations : []).map(value => object(value) as NonNullable<Trace['operation']>),
      attributeNodeCount: new Set(calls.filter(call => call.operation === 'attr')
        .flatMap(call => Array.isArray(call.arguments) && call.arguments[0]?.type === 'reference' ? [call.arguments[0].reference] : [])).size,
      callCount: calls.length, calls, completed: recording.completed !== false, incomplete: recording.incomplete ?? null, failure: recording.failure ?? null };
  });
  if (capture.droppedQueryRecordings) warnings.push('查询录制达到会话上限，部分查询没有节点访问记录。');
  if (recordings.some(recording => !recording.completed)) warnings.push('部分查询仍未完成；已保留读取到的数据，result 为空不代表未命中。');
  if (recordings.some(recording => recording.incomplete)) warnings.push('部分节点访问记录不完整，未记录的访问不能视为没有发生。');
  if (!events.length) warnings.push('没有采集到事件；这不等于规则没有命中。');

  const groups = new Map<number, Trace[]>();
  const reasons = new Map<string, { stage: string; reason: string; rule: string | null; count: number; lastSequence: number }>();
  for (const event of events) {
    if (event.runId > 0) {
      const group = groups.get(event.runId) ?? [];
      group.push(event);
      groups.set(event.runId, group);
    }
    const key = JSON.stringify([event.stage, event.reason, event.rule ?? null]);
    const reason = reasons.get(key) ?? { stage: event.stage, reason: event.reason, rule: event.rule ?? null, count: 0, lastSequence: 0 };
    reason.count++;
    reason.lastSequence = event.sequence;
    reasons.set(key, reason);
  }
  const millis = (nanos: number) => Math.round(nanos / 1000) / 1000;
  const openOperations = Array.isArray(session.openOperations) ? session.openOperations : [];
  const operationsAtStop = Array.isArray(session.operationsAtStop) ? session.operationsAtStop : [];
  if (openOperations.length) warnings.push('存在尚未记录返回的调用；缺少完成证据不能等同于调用现在仍在执行。');
  if (session.omittedOperations) warnings.push('并发调用记录达到上限，部分调用未记录。');
  const operationValues = [...recordings.flatMap(recording => recording.operations),
    ...events.flatMap(event => event.operation ? [event.operation] : [])];
  const operations = [...new Map(operationValues.filter(operation => typeof operation.durationNanos === 'number')
    .map(operation => [operation.id, { ...operation, durationMillis: millis(operation.durationNanos as number) }])).values()];
  const queueWaits = events.filter(event => event.taskId != null && event.entrySource === 'coordinator').map(event => {
    const start = events.find(candidate => candidate.taskId === event.taskId && candidate.reason === 'query_started');
    return { taskId: event.taskId, generation: event.generation, blockingRunId: event.blockingRunId ?? null,
      waitMillis: start ? millis(start.elapsedNanos - event.elapsedNanos) : null };
  });
  const requests = (Array.isArray(capture.runs) ? capture.runs : []).map(value => {
    const run = object(value);
    const request = object(run.request);
    return { requestId: request.requestId, engineRunId: run.engineRunId ?? null, rule: request.rule, execute: request.execute ?? false,
      phase: run.phase, result: run.result ?? null, error: run.error ?? null };
  });
  const system = capture.systemRecording ? object(capture.systemRecording) : null;
  const timeline = (Array.isArray(system?.steps) ? system.steps : []).map(value => object(value));
  const finishedRunIds = new Set(timeline.flatMap(step => {
    const command = step.command == null ? null : object(step.command);
    return command?.type === 'finish' && typeof command.run === 'number' ? [command.run] : [];
  }));
  const requestTerminals = new Map(requests.flatMap(request => {
    const terminal = ({ completed: 'Completed', cancelled: 'Cancelled', failed: 'Failed' } as Record<string, string>)[String(request.phase)];
    return typeof request.engineRunId === 'number' && terminal ? [[request.engineRunId, terminal] as const] : [];
  }));
  const runs = [...new Set([...groups.keys(), ...finishedRunIds,
    ...requests.map(request => request.engineRunId).filter((id): id is number => typeof id === 'number'),
    ...recordings.map(recording => recording.runId).filter((id): id is number => typeof id === 'number'),
  ])].map(runId => {
    const trace = groups.get(runId) ?? [];
    const start = trace.find(event => event.stage === 'Query' && event.reason === 'query_started');
    const terminalEvent = trace.find(event => ['Completed', 'Cancelled', 'Failed'].includes(event.stage));
    const cancelling = trace.find(event => event.stage === 'Cancelling');
    const last = trace.at(-1);
    const terminal = terminalEvent?.stage ?? requestTerminals.get(runId) ?? (finishedRunIds.has(runId) ? 'Finished' : null);
    const terminalSource = terminalEvent ? 'trace' : requestTerminals.has(runId) ? 'request' : finishedRunIds.has(runId) ? 'scheduler' : null;
    const duration = (from: Trace | undefined) => from && terminalEvent && terminalEvent.elapsedNanos >= from.elapsedNanos
      ? millis(terminalEvent.elapsedNanos - from.elapsedNanos) : null;
    return {
      runId, firstSequence: trace[0]?.sequence ?? null, lastSequence: last?.sequence ?? null,
      terminal, terminalSource, lastStage: last?.stage ?? null, lastReason: last?.reason ?? null,
      durationMillis: duration(start), cancellationMillis: duration(cancelling),
      cancellationReason: cancelling?.reason ?? null,
    };
  });
  const unfinished = runs.filter(run => run.terminal === null);
  if (unfinished.length) warnings.push('部分运行缺少阶段终态、请求终态及调度收尾证据，不能当成成功或卡死。');
  if (requests.some(run => run.phase === 'running' || run.phase === 'cancelling')) {
    warnings.push('单次请求尚未结束：停止采集不等于物理输入已释放。');
  }
  const topReasons = [...reasons.values()].sort((a, b) => b.count - a.count || b.lastSequence - a.lastSequence).slice(0, 20);
  if (system && !system.sealed) warnings.push('系统诊断尚未结束：已保留当前现场，不代表动作已完成；收尾后可再次导出。');
  const engine = capture.runtime && object(capture.runtime).engine
    ? object(object(capture.runtime).engine) : null;
  const cleanupConfirmed = runs.some(run => run.runId === engine?.activeRunId && run.terminal !== null) ||
    (engine?.activeRunId != null && system?.sealed === true && system.finishingRun === engine.activeRunId);
  if (engine?.cancelling === true && engine.attached === false && !cleanupConfirmed) {
    warnings.push('停止时服务已断开且截至导出仍缺少对应运行的收尾证据：系统可能不再交付手势回调；这不是当前运行状态，不能据此认定应用现在仍然卡住。');
  }
  if (system?.incomplete) warnings.push(`系统录制不完整：${system.incomplete}`);
  const systemRuns = new Map<number, Record<string, unknown>[]>();
  for (const step of timeline) {
    const command = step.command == null ? null : object(step.command);
    const ids = new Set<number>();
    if (typeof command?.run === 'number') ids.add(command.run);
    for (const value of Array.isArray(step.effects) ? step.effects : []) {
      const effect = object(value);
      if (effect.run != null) {
        const run = object(effect.run);
        if (typeof run.id === 'number') ids.add(run.id);
      }
    }
    for (const id of ids) systemRuns.set(id, [...(systemRuns.get(id) ?? []), step]);
  }
  const runIds = new Set([...runs.map(run => run.runId), ...systemRuns.keys(),
    ...openOperations.map(value => object(value).runId).filter((id): id is number => typeof id === 'number')]);
  const runDetails = [...runIds].map(runId => ({
    runId,
    eventSequences: (groups.get(runId) ?? []).map(event => event.sequence),
    queryIndexes: recordings.flatMap((recording, index) => recording.runId === runId ? [index] : []),
    systemSequences: (systemRuns.get(runId) ?? []).map(step => step.sequence),
    operationIds: [...new Set([
      ...operationValues.filter(operation => operation.runId === runId).map(operation => operation.id),
      ...openOperations.map(object).filter(operation => operation.runId === runId).map(operation => operation.id),
    ])],
  }));
  const nodeCalls = recordings.flatMap((recording, queryIndex) => recording.calls.map(call => ({
    queryIndex, runId: recording.runId, rule: recording.rule, queryOperationId: recording.operationId, ...call,
  })));
  const attention = events.filter(event => ['Failed', 'Cancelling', 'Cancelled'].includes(event.stage));
  const conditions = events.filter(event => event.condition != null);
  const eventDrops = events.filter(event => event.eventDrop != null);
  const nodePressure = events.filter(event => event.nodePressure != null);
  const incompleteQueries = events.filter(event => ['query_child_limit', 'query_descendant_limit', 'query_child_unavailable', 'query_root_unavailable'].includes(event.reason));
  if (eventDrops.length) warnings.push('无障碍事件队列曾溢出；旧事件已释放，不能把丢弃的事件当成已完成查询。');
  if (nodePressure.length) warnings.push('节点读取曾超时或耗尽并发容量；取消等待不代表 Binder 已返回，最长耗时只覆盖本会话跟踪的读取。');
  if (incompleteQueries.length) warnings.push('查询不完整，对应规则本次动作已终止；不能将其解释为排除条件未命中。');
  if (conditions.some(event => event.condition?.kind === 'limit_reached')) warnings.push('属性解释达到记录上限，后续条件继续求值但不再展开；原始匹配结果不受影响。');
  const statistics = capture.operationStatistics == null ? null : object(capture.operationStatistics);
  const operationTotals = (Array.isArray(statistics?.totals) ? statistics.totals : []).map(value => {
    const total = object(value);
    const slowest = object(total.slowest);
    const count = Number(total.count);
    const totalMillis = Number(total.totalDurationNanos) / 1_000_000;
    return { name: total.name, rule: total.rule ?? null, count, totalMillis,
      meanMillis: count > 0 ? totalMillis / count : null,
      slowest: { ...slowest, durationMillis: Number(slowest.durationNanos) / 1_000_000 } };
  }).sort((a, b) => b.totalMillis - a.totalMillis);
  if (statistics?.omitted) warnings.push('全程调用统计达到分组上限，部分调用未纳入分组；已有分组仍持续累计。');
  if (!statistics && (session.dropped || capture.droppedQueryRecordings)) warnings.push('本记录未采集全程调用统计，耗时排名仅代表保留下来的详细片段。');
  const range = (values: unknown[]) => {
    const times = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
    return { firstElapsedMillis: times.length ? Math.min(...times) : null,
      lastElapsedMillis: times.length ? Math.max(...times) : null };
  };
  const coverage = {
    events: { ...range(events.map(event => event.elapsedNanos / 1_000_000)), retained: events.length, dropped: session.dropped ?? 0 },
    queries: { ...range(recordings.flatMap(q => [q.startedAt, q.endedAt]).map(time =>
      typeof time === 'number' && typeof session.startedAt === 'number' ? time - session.startedAt : null)),
      retained: recordings.length, dropped: capture.droppedQueryRecordings ?? 0 },
    system: { ...range(timeline.map(step => typeof step.timeMillis === 'number' && typeof system?.startedAtMillis === 'number'
      ? step.timeMillis - system.startedAtMillis : null)), retained: timeline.length, incomplete: system?.incomplete ?? null },
  };
  const result = {
    input, session: { id: session.id, active: session.active, detailed: session.detailed ?? false,
      ruleFilter: session.ruleFilter ?? null, dropped: session.dropped ?? 0, startedAt: session.startedAt ?? null,
      stoppedAt: session.stoppedAt ?? null, trackingComplete: session.trackingComplete ?? null },
    warnings, coverage,
    entryTotals: Array.isArray(capture.entries) ? capture.entries : [],
    operationStatistics: statistics ? { completed: statistics.completed, omitted: statistics.omitted, totals: operationTotals } : null,
    statisticsNote: '全程统计覆盖本会话已返回且成功建立跟踪的调用，包含停止后的返回；并发跟踪和分组遗漏分别见 omittedOperations、operationStatistics.omitted。Query/SelectorQuery 按规则分组，其余按 API 名称分组。不同层级与并发调用的耗时可能重叠，不能相加当作运行总耗时。最慢调用保留关联 ID，其详细事件可能已被覆盖。coverage 为保留详情的起止范围，不代表区间内连续完整；查询范围由墙钟换算，其余使用单调时钟。',
    openOperations, omittedOperations: session.omittedOperations ?? 0,
    slowestOperations: operations.sort((a, b) => b.durationMillis - a.durationMillis).slice(0, 20),
    operations,
    operationsAtStop,
    completedAfterStop: events.filter(event => event.reason === 'operation_finished_after_stop').map(event => event.operation),
    slowestNodeCalls: nodeCalls.filter(call => call.durationMillis != null)
      .sort((a, b) => b.durationMillis! - a.durationMillis!).slice(0, 20),
    runDetails,
    inputs: events.filter(event => event.input != null).map(event => ({ sequence: event.sequence,
      elapsedNanos: event.elapsedNanos, input: event.input, decision: event.inputDecision })),
    queueWaits,
    eventDrops, nodePressure, incompleteQueries,
    counts: { events: events.length, runs: runs.length, unfinished: unfinished.length,
      failed: runs.filter(run => run.terminal === 'Failed').length,
      cancelled: runs.filter(run => run.terminal === 'Cancelled').length,
      completed: runs.filter(run => run.terminal === 'Completed').length,
      finishedWithoutOutcome: runs.filter(run => run.terminal === 'Finished').length,
      ruleSnapshots: Array.isArray(capture.ruleSnapshots) ? capture.ruleSnapshots.length : 0,
      droppedRuleSnapshots: capture.droppedRuleSnapshots ?? 0 },
    timingNote: 'durationMillis 是 query_started 到终态的运行总耗时，包含等待与动作；cancellationMillis 仅在存在 Cancelling 和终态记录时计算，不代表触点抬起延迟。缺失边界为 null。Completed 不表示匹配成功；Finished 仅表示调度收尾，未推断执行结果。请求终态可补齐状态，但不补造阶段时间戳。',
    serviceTimeline: events.filter(event => event.serviceConnection).map(event => ({ atMillis: event.elapsedNanos / 1e6, ...event.serviceConnection })),
    evidenceCoverage: {
      boundary: { available: Array.isArray(capture.boundaryEvents), count: boundaryEvents.length, dropped: capture.droppedBoundaryEvents ?? null },
      detail: { count: detailEvents.length, dropped: session.dropped ?? 0, firstMillis: detailEvents[0]?.elapsedNanos == null ? null : detailEvents[0].elapsedNanos / 1e6, lastMillis: detailEvents.at(-1)?.elapsedNanos == null ? null : detailEvents.at(-1)!.elapsedNanos / 1e6 },
      key: { available: Array.isArray(capture.keyEvents), count: keyEvents.length, dropped: capture.droppedKeyEvents ?? null, firstMillis: keyEvents[0]?.elapsedNanos == null ? null : keyEvents[0].elapsedNanos / 1e6, lastMillis: keyEvents.at(-1)?.elapsedNanos == null ? null : keyEvents.at(-1)!.elapsedNanos / 1e6 },
    },
    pageLatency: matchingLatency(events),
    pageLatencyNote: '时间相对诊断开始。首次节点观测不是页面绘制时间；服务连接缺失为 null。queryToMatchMillis 包含未命中后的间隔，queryExecutionMillis 仅累加 Query 执行耗时，两者不能相加。节点年龄和读取 ID 只证明使用来源，不能单独归因于微信或系统缓存。缺少节点来源的旧记录不生成页面推断。',
    slowestQueries: recordings.filter(recording => recording.durationMillis !== null)
      .map(({ calls, operations, ...summary }) => summary).sort((a, b) => b.durationMillis! - a.durationMillis!).slice(0, 20),
    slowestRuns: runs.filter(run => run.durationMillis !== null).sort((a, b) => b.durationMillis! - a.durationMillis!).slice(0, 20),
    runs,
    unfinishedRuns: unfinished.slice(-20),
    recentAttention: attention.slice(-20),
    omittedAttention: Math.max(0, attention.length - 20),
    recentConditions: conditions.slice(-20), omittedConditions: Math.max(0, conditions.length - 20),
    queryRecordings: recordings, droppedQueryRecordings: capture.droppedQueryRecordings ?? 0,
    systemRecording: system ? { steps: Array.isArray(system.steps) ? system.steps.length : 0,
      sealed: system.sealed, incomplete: system.incomplete ?? null, initial: system.initial, final: system.final,
      startedAtMillis: system.startedAtMillis, stoppedAtMillis: system.stoppedAtMillis, finishingRun: system.finishingRun,
      timeline } : null,
    nodeTimingNote: '节点调用 startedNanos 相对所属查询记录开始；durationNanos 是实际委托调用耗时。operationId 关联会话内操作，parentOperationId 关联父操作。NodeRead:* 是平台实际调用，*Wait 包含调度与等待；两者可能重叠，不能相加作为总耗时。',
    actionResults: events.filter(event => event.action != null),
    actionOutcomes: timeline.flatMap(step => {
      const command = step.command == null ? null : object(step.command);
      return command?.type === 'action_end' ? [{ runId: command.run, timeMillis: step.timeMillis, outcome: command.outcome }] : [];
    }),
    topReasons, omittedReasonGroups: Math.max(0, reasons.size - 20),
    requests, runtime: capture.runtime ?? null, runtimeCapturedAt: capture.runtimeCapturedAt ?? null,
    appId: capture.appId ?? null, appVersion: capture.appVersion ?? null, platform: capture.platform ?? null,
  };
  if (output) await writeFile(output, JSON.stringify(result, null, 2));
  return result;
}
