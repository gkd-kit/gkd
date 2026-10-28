import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/** Read subscription JSON without modifying its source; retain only coordinate expressions. */
export async function collectExpressionCorpus(input: string, output: string) {
  const counts = new Map<string, number>();
  let files = 0;
  const coordinates = ['left', 'top', 'right', 'bottom', 'x', 'y'];
  function position(value: unknown) {
    if (!value || typeof value !== 'object') return;
    for (const key of coordinates) {
      const source = (value as Record<string, unknown>)[key];
      if (typeof source === 'string' || typeof source === 'number') {
        const expression = String(source);
        counts.set(expression, (counts.get(expression) ?? 0) + 1);
      }
    }
  }
  function visit(value: unknown) {
    if (!value || typeof value !== 'object') return;
    if (Array.isArray(value)) { value.forEach(visit); return; }
    const object = value as Record<string, unknown>;
    position(object.position);
    if (object.swipeArg && typeof object.swipeArg === 'object') {
      const swipe = object.swipeArg as Record<string, unknown>;
      position(swipe.start); position(swipe.end);
    }
    Object.values(object).forEach(visit);
  }
  for (const entry of await readdir(input, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith('.json')) continue;
    visit(JSON.parse(await readFile(join(input, entry.name), 'utf8')));
    files++;
  }
  const expressions = [...counts.keys()].sort();
  const report = { files, occurrences: [...counts.values()].reduce((a, b) => a + b, 0), expressions };
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(report, null, 2));
  return { files, occurrences: report.occurrences, unique: expressions.length, output };
}
