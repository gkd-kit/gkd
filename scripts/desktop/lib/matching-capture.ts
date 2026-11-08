/** Expand the on-disk/HTTP query dictionaries for analysis; old aggregate captures pass through. */
export function expandMatchingCapture(capture: Record<string, any>): Record<string, any> {
  if (capture.formatVersion !== 2) return capture;
  const plans = capture.queryPlans;
  const summaries = capture.querySummaries;
  const details = capture.queryDetails;
  if (!Array.isArray(plans) || !Array.isArray(summaries) || !Array.isArray(details))
    throw new Error('Missing diagnostic query sections');
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
  return { ...capture, queryRecordings: recordings };
}
