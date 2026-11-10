function expandRow(value: any, shapes: unknown[]): any {
  if (!Array.isArray(value)) return value;
  const [index, ...fields] = value;
  if (index === -1) return fields.map(item => expandRow(item, shapes));
  const names = shapes[index];
  if (!Number.isInteger(index) || !Array.isArray(names) || names.length !== fields.length ||
      names.some(name => typeof name !== 'string'))
    throw new Error('Invalid diagnostic row shape');
  return Object.fromEntries(names.map((name, i) => [name, expandRow(fields[i], shapes)]));
}

/** Expand on-disk/HTTP diagnostic dictionaries for analysis. */
export function expandMatchingCapture(capture: Record<string, any>): Record<string, any> {
  const version = capture.formatVersion;
  if (version !== 2 && version !== 3 && version !== 4) return capture;
  const plans = capture.queryPlans;
  const summaries = capture.querySummaries;
  const rows = capture.queryDetails;
  if (!Array.isArray(plans) || !Array.isArray(summaries) || !Array.isArray(rows))
    throw new Error('Missing diagnostic query sections');
  const details = version === 4 ? rows.map(row => {
    if (!Array.isArray(row) || row.length !== 2 || !Array.isArray(row[0]) || !Array.isArray(row[1]))
      throw new Error('Invalid packed query detail');
    return expandRow(row[1], row[0]);
  }) : rows;
  const byId = new Map(details.map(detail => [detail.queryId, detail]));
  if (byId.size !== details.length) throw new Error('Duplicate diagnostic query detail ID');
  const recordings = summaries.map(summary => {
    if (!Number.isInteger(summary.planId) || !plans[summary.planId]) throw new Error('Invalid query plan reference');
    const detail = byId.get(summary.queryId);
    if (summary.detailsRetained && !detail) throw new Error('Missing retained query detail');
    function expand(value: any): any {
      if (!value || typeof value !== 'object') return value;
      // Preserve untouched nodes, including entire details without text references.
      let result = value;
      for (const [key, item] of Object.entries(value)) {
        if (key === 'valueRef') {
          if (!Number.isInteger(item) || typeof detail?.texts?.[item as number] !== 'string')
            throw new Error('Invalid query text reference');
          if (result === value) result = { ...value };
          delete result.valueRef;
          result.value = detail.texts[item as number];
        } else {
          const next = expand(item);
          if (next !== item) {
            if (result === value) result = Array.isArray(value) ? [...value] : { ...value };
            result[key] = next;
          }
        }
      }
      return result;
    }
    const { planId, ...header } = summary;
    return { ...header, plan: plans[planId], calls: expand(detail?.calls ?? []), operations: expand(detail?.operations ?? []) };
  });
  if (version === 2) return { ...capture, queryRecordings: recordings };
  const eventShapes = capture.eventShapes;
  if (version === 4 && !Array.isArray(eventShapes)) throw new Error('Missing diagnostic event shapes');

  const timelines = Object.fromEntries(['events', 'keyEvents', 'boundaryEvents'].map(section => {
    const rows = capture[section];
    const strings = capture.traceStrings?.[section];
    if (!Array.isArray(rows) || !Array.isArray(strings))
      throw new Error(`Missing diagnostic timeline: ${section}`);
    function resolveString(value: unknown): unknown {
      if (typeof value !== 'number') return value;
      if (!Number.isInteger(value) || typeof strings[value] !== 'string')
        throw new Error(`Invalid diagnostic string reference in ${section}`);
      return strings[value];
    }
    return [section, rows.map(row => {
      const trace = section === 'events' && version === 4 ? expandRow(row, eventShapes) : row;
      const { rule, condition, operation, input } = trace;
      return {
        ...trace,
        ...(typeof rule === 'number' && { rule: resolveString(rule) }),
        ...(typeof condition?.selector === 'number' && {
          condition: { ...condition, selector: resolveString(condition.selector) },
        }),
        ...(typeof operation?.rule === 'number' && {
          operation: { ...operation, rule: resolveString(operation.rule) },
        }),
        ...(input && !Object.hasOwn(input, 'policy') && {
          input: { ...input, policy: capture.session.policy },
        }),
      };
    })];
  }));
  return { ...capture, ...timelines, queryRecordings: recordings };
}
