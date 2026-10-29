import assert from 'node:assert/strict';
import test from 'node:test';
import { createDebugWait, waitForDebugRun, type MatchDebugRun } from './match-debug-runs.ts';

test('run wait continues through cancellation until cleanup completes', async () => {
  const states: MatchDebugRun[] = [{ phase: 'running' }, { phase: 'cancelling' }, { phase: 'cancelled' }];
  const wait = createDebugWait(new AbortController().signal, 1000, 0, 'test');
  let reads = 0;
  const result = await waitForDebugRun(async () => states[reads++]!, wait, 1000);
  assert.equal(result.phase, 'cancelled');
  assert.equal(reads, 3);
});

test('aborted checks stop reading but cleanup waits can finish', async () => {
  const controller = new AbortController();
  controller.abort(new Error('stop test'));
  const wait = createDebugWait(controller.signal, 1000, 0, 'test');
  let reads = 0;
  const read = async () => ++reads;
  await assert.rejects(wait(read, () => true), /stop test/);
  assert.equal(reads, 0);
  assert.equal(await wait(read, () => true, 1000, false), 1);
});

test('timeout reports the last observed state', async () => {
  const wait = createDebugWait(new AbortController().signal, 0, 0, 'test');
  await assert.rejects(wait(async () => ({ phase: 'cancelling' }), () => false),
    /test timed out:.*cancelling/);
});
