import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { repositoryRoot } from '../lib/local-paths.ts';

interface BinderSection {
  name: string; sections: number; binderTransactions: number;
  onewayTransactions: number; synchronousBinderMillis: number;
}
export interface BinderTrace {
  sections: BinderSection[]; processes: number; unfinishedSections: number;
  unfinishedTransactions: number; traceIssues: { name: string; index: number; value: number }[];
}
export interface NodeBenchmark {
  samples: { cache: string; level: string; measurements: {
    nodes: { reads: { operation: string; calls: number }[] };
  }[] }[];
}

/** Cross-check independently collected trace markers against measured node calls. */
export function checkBinderTrace(trace: BinderTrace, benchmark: NodeBenchmark) {
  const problems: string[] = [];
  if (trace.processes !== 1) problems.push('Expected exactly one benchmark app process');
  if (trace.unfinishedSections) problems.push('Benchmark sections are unfinished');
  if (trace.unfinishedTransactions) problems.push('Binder transactions are unfinished or cross a section boundary');
  if (trace.traceIssues.length) problems.push('Trace reports errors or data loss');
  const expected = new Map<string, number>();
  const cases = benchmark.samples.map(sample => {
    const prefix = `GKD.benchmark.${sample.cache}.${sample.level}.measured.`;
    if (expected.has(prefix + 'selector')) throw new Error('Duplicate benchmark case');
    expected.set(prefix + 'selector', sample.measurements.length);
    for (const operation of ['Root', 'Refresh']) {
      const calls = sample.measurements.reduce((total, m) => total +
        (m.nodes.reads.find(r => r.operation === operation)?.calls ?? 0), 0);
      // RefreshRoot refreshes inside the root section; only target refresh has its own marker.
      const markedCalls = operation === 'Refresh' && sample.cache === 'RefreshRoot'
        ? calls - sample.measurements.reduce((total, m) => total + (m.nodes.reads.find(r => r.operation === 'Root')?.calls ?? 0), 0) : calls;
      if (markedCalls > 0) expected.set(prefix + operation.toLowerCase(), markedCalls);
    }
    const sections = trace.sections.filter(s => s.name.startsWith(prefix));
    const transactions = sections.reduce((n, s) => n + s.binderTransactions, 0);
    return { cache: sample.cache, level: sample.level, iterations: sample.measurements.length,
      binderTransactions: transactions,
      transactionsPerQuery: sample.measurements.length ? transactions / sample.measurements.length : null,
      onewayTransactions: sections.reduce((n, s) => n + s.onewayTransactions, 0),
      synchronousBinderMillis: sections.reduce((n, s) => n + s.synchronousBinderMillis, 0) };
  });
  if (!cases.length || cases.some(c => !c.iterations)) problems.push('Benchmark has no measured queries');
  const actual = new Map(trace.sections.map(s => [s.name, s.sections]));
  for (const [name, count] of expected) if (actual.get(name) !== count) {
    problems.push(`Section coverage mismatch: ${name}, expected ${count}, found ${actual.get(name) ?? 0}`);
  }
  for (const name of actual.keys()) if (!expected.has(name)) problems.push(`Unexpected benchmark section: ${name}`);
  if (!cases.some(c => c.binderTransactions > 0)) problems.push('No Binder evidence inside measured sections');
  return { verified: problems.length === 0, problems, cases, trace,
    scope: 'Measured synchronous node/selector sections on the app calling thread; innermost attribution; no physical input',
    timingNote: 'Synchronous Binder time excludes oneway transactions; tracing perturbs timing. Counts do not equal node API calls.' };
}

export async function summarizeBinder(input: string, tracePath: string, processor: string, output: string) {
  const command = resolve(processor);
  const args = ['query', '-f', join(repositoryRoot, 'scripts/desktop/queries/android-benchmark.sql'), resolve(tracePath)];
  const { stdout } = await promisify(execFile)(command.endsWith('.exe') ? command : 'python',
    command.endsWith('.exe') ? args : [command, ...args],
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 8 * 1024 * 1024 });
  // Trace Processor emits one CSV field containing the JSON object produced by the SQL.
  const lines = stdout.trim().split(/\r?\n/);
  if (lines.length !== 2 || lines[0] !== '"report"' || !lines[1]!.startsWith('"') || !lines[1]!.endsWith('"')) {
    throw new Error('Unexpected Trace Processor output; expected one report column');
  }
  const trace: BinderTrace = JSON.parse(lines[1]!.slice(1, -1).replaceAll('""', '"'));
  const report = checkBinderTrace(trace, JSON.parse(await readFile(input, 'utf8')));
  await writeFile(output, JSON.stringify({ ...report, input: resolve(input), tracePath: resolve(tracePath) }, null, 2));
  if (!report.verified) throw new Error(`Binder verification failed; evidence saved to ${output}: ${report.problems.join('; ')}`);
  return report;
}
