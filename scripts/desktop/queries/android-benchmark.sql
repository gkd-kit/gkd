-- Run with Perfetto Trace Processor. Only synchronous, measured node/selector
-- sections participate. Prefer the innermost section so a root cache miss inside
-- a selector cannot count the same Binder transaction twice.
CREATE PERFETTO TABLE benchmark_sections AS
SELECT s.id, s.track_id, s.ts, s.dur, s.depth, s.name, p.upid
FROM slice s JOIN thread_track t ON s.track_id = t.id
JOIN thread th ON t.utid = th.utid JOIN process p ON th.upid = p.upid
WHERE s.name GLOB 'GKD.benchmark.*.measured.*' AND p.name = 'li.songe.gkd.debug';

CREATE PERFETTO TABLE benchmark_binder AS
WITH candidates AS (
  SELECT b.id, b.dur, b.name AS binder_kind, s.id AS section_id,
    ROW_NUMBER() OVER (PARTITION BY b.id ORDER BY s.depth DESC) AS priority
  FROM slice b JOIN benchmark_sections s
    ON b.track_id = s.track_id AND b.ts >= s.ts AND b.ts < s.ts + s.dur
  WHERE b.name IN ('binder transaction', 'binder transaction async') AND s.dur >= 0
)
SELECT * FROM candidates WHERE priority = 1;

CREATE PERFETTO TABLE benchmark_summary AS
SELECT s.name, COUNT(DISTINCT s.id) AS sections,
  COUNT(b.id) AS binder_transactions,
  SUM(CASE WHEN b.binder_kind = 'binder transaction async' THEN 1 ELSE 0 END) AS oneway_transactions,
  COALESCE(SUM(CASE WHEN b.binder_kind = 'binder transaction' AND b.dur >= 0 THEN b.dur ELSE 0 END), 0) / 1000000.0 AS synchronous_binder_ms
FROM benchmark_sections s LEFT JOIN benchmark_binder b ON s.id = b.section_id
GROUP BY s.name ORDER BY s.name;

-- One machine-readable result: missing/end-truncated markers and incomplete
-- transactions must not silently disappear from a successful report.
SELECT json_object(
  'sections', (SELECT json_group_array(json_object('name', name, 'sections', sections,
    'binderTransactions', binder_transactions, 'onewayTransactions', oneway_transactions,
    'synchronousBinderMillis', synchronous_binder_ms)) FROM benchmark_summary),
  'processes', (SELECT COUNT(DISTINCT upid) FROM benchmark_sections),
  'unfinishedSections', (SELECT COUNT(*) FROM benchmark_sections WHERE dur < 0),
  'unfinishedTransactions', (SELECT COUNT(*) FROM benchmark_binder b
    JOIN benchmark_sections s ON b.section_id = s.id JOIN slice raw ON b.id = raw.id
    WHERE b.dur < 0 OR raw.ts + raw.dur > s.ts + s.dur),
  'traceIssues', (SELECT json_group_array(json_object('name', name, 'index', idx, 'value', value))
    FROM stats WHERE severity IN ('error', 'data_loss') AND value != 0)
) AS report;
