type ServiceMode = 'A11y' | 'Automation';
export type NodeProvenance = {
  readId: number; connectionId: number; service: ServiceMode;
  source: 'Root' | 'Event' | 'Refresh'; reason: 'Event' | 'SourceReused' | 'MissingOrExpired' | 'ForegroundConfirmation';
  page: number; appId: string; observedActivityId: string | null; windowId: number;
  acquiredAtMillis: number; ageMillis: number; parentReadId?: number | null;
};

export type LatencyTrace = {
  sequence: number; elapsedNanos: number; runId: number; reason: string;
  rule?: string | null; entrySource?: string | null; node?: NodeProvenance | null;
  nodeRole?: 'Source' | 'Root' | null;
  serviceConnection?: { event: 'Connected' | 'QueryStarted' | 'HandoffShutdownRequested' | 'Disconnected'; service: ServiceMode; connectionId: number; targetConnectionId?: number | null } | null;
  pageObservation?: { page: number; appId: string; activityId: string | null; firstConfirmation?: boolean | null } | null;
  fastQuery?: { kind: 'Text' | 'Id' | 'Vid'; value: string; count: number;
    anomalousCandidates?: { className: string | null; viewId: string | null;
      contentDescription: string | null; windowId: number; visibleToUser: boolean }[] } | null;
  operation?: { name: string; startedNanos: number; durationNanos?: number | null; outcome?: string | null } | null;
};

const selectorOutcome = /^(matches|anyMatches|excludeMatches|excludeAllMatches)\[\d+\]:/;
const at = (event: LatencyTrace | undefined) => event ? event.elapsedNanos / 1e6 : null;

type QueryEvidence = {
  runId: number; rule: string | null;
  operation?: NonNullable<LatencyTrace['operation']>;
  selectorOutcomes: string[]; nodeReads: Set<number>; fastQueries: NonNullable<LatencyTrace['fastQuery']>[];
};

function summarizeQuery(query: QueryEvidence) {
  return { runId: query.runId, rule: query.rule,
    startedMillis: query.operation ? query.operation.startedNanos / 1e6 : null,
    durationMillis: query.operation?.durationNanos == null ? null : query.operation.durationNanos / 1e6,
    outcome: query.operation?.outcome ?? null,
    selectorOutcomes: query.selectorOutcomes, nodeReads: [...query.nodeReads], fastQueries: query.fastQueries,
  };
}

/** Observed boundaries only: no inference that an unsuccessful query means the UI was absent. */
export function matchingLatency(events: LatencyTrace[]) {
  const pages = new Map<number, { identity: { appId: string; activityId: string | null }; events: LatencyTrace[]; runs: Set<number> }>();
  const runs = new Map<number, LatencyTrace[]>();
  const connections = new Map<number, LatencyTrace>();
  const queries = new Map<number, Map<string | null, QueryEvidence>>();
  for (const event of events) {
    if (event.serviceConnection?.event === 'Connected') connections.set(event.serviceConnection.connectionId, event);
    if (event.runId > 0) {
      const run = runs.get(event.runId) ?? [];
      run.push(event);
      runs.set(event.runId, run);
      const isOutcome = selectorOutcome.test(event.reason);
      if (event.operation?.name === 'Query' || isOutcome || event.nodeRole != null || event.fastQuery) {
        const byRule = queries.get(event.runId) ?? new Map<string | null, QueryEvidence>();
        const rule = event.rule ?? null;
        const query: QueryEvidence = byRule.get(rule) ?? { runId: event.runId, rule, selectorOutcomes: [], nodeReads: new Set<number>(), fastQueries: [] };
        if (event.operation?.name === 'Query') query.operation = event.operation;
        if (isOutcome) query.selectorOutcomes.push(event.reason);
        if (event.node) query.nodeReads.add(event.node.readId);
        if (event.fastQuery) query.fastQueries.push(event.fastQuery);
        byRule.set(rule, query);
        queries.set(event.runId, byRule);
      }
    }
    const identity = event.node
      ? { page: event.node.page, appId: event.node.appId, activityId: event.node.observedActivityId }
      : event.pageObservation;
    if (!identity) continue;
    const page = pages.get(identity.page) ?? { identity, events: [], runs: new Set<number>() };
    page.events.push(event);
    if (event.runId > 0) page.runs.add(event.runId);
    pages.set(identity.page, page);
  }
  return [...pages].map(([pageId, page]) => {
    const related = [...page.runs].flatMap(run => runs.get(run) ?? []).sort((a, b) => a.sequence - b.sequence);
    const firstQuery = related.find(event => event.operation?.name === 'Query');
    const firstMatch = related.find(event => event.reason === 'matched');
    const action = related.find(event => event.reason === 'action_succeeded');
    const confirmed = page.events.find(event => event.pageObservation?.firstConfirmation === true);
    const services = [...new Map(page.events.flatMap(event => event.node ? [[event.node.connectionId, event.node] as const] : [])).values()];
    const pageQueries = [...page.runs].flatMap(run => [...(queries.get(run)?.values() ?? [])]);
    const completed = pageQueries.filter(query => query.operation?.durationNanos != null);
    const uses = page.events.filter(event => event.nodeRole != null);
    return {
      page: pageId, appId: page.identity.appId, activityId: page.identity.activityId,
      foregroundConfirmedMillis: at(confirmed),
      firstNodeObservedMillis: at(page.events.find(event => event.node)),
      firstQueryMillis: firstQuery?.operation ? firstQuery.operation.startedNanos / 1e6 : null,
      firstMatchedMillis: at(firstMatch), actionSucceededMillis: at(action),
      observedToActionMillis: action ? (action.elapsedNanos - page.events[0]!.elapsedNanos) / 1e6 : null,
      confirmedToActionMillis: confirmed && action ? (action.elapsedNanos - confirmed.elapsedNanos) / 1e6 : null,
      queryToMatchMillis: firstQuery?.operation && firstMatch ? (firstMatch.elapsedNanos - firstQuery.operation.startedNanos) / 1e6 : null,
      completedQueries: completed.length,
      notMatchedQueries: completed.filter(query => query.operation?.outcome === 'not_matched').length,
      queryExecutionMillis: completed.reduce((sum, query) => sum + query.operation!.durationNanos! / 1e6, 0),
      queries: completed.map(summarizeQuery),
      incompleteQueries: pageQueries.filter(query => query.operation?.durationNanos == null).map(summarizeQuery),
      services: services.map(node => ({ service: node.service, connectionId: node.connectionId,
        connectedMillis: at(connections.get(node.connectionId)),
      })),
      nodeUses: uses.map(event => ({ runId: event.runId, rule: event.rule, atMillis: at(event), role: event.nodeRole, ...event.node })),
    };
  });
}
