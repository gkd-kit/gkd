import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { join } from 'node:path';
import { test } from 'node:test';
import { matchingDebug } from '../commands/matching-debug.ts';
import { DesktopClient, type DesktopRequestOptions } from './desktop-client.ts';
import { assertDesktopTestPath, desktopTests } from './local-paths.ts';

test('Android benchmark can return after the ordinary HTTP timeout but within its 120s server budget', async t => {
  await mkdir(desktopTests, { recursive: true });
  const directory = await mkdtemp(join(desktopTests, 'benchmark-timeout-'));
  const input = join(directory, 'input.json');
  const body = { request: { rule: 'benchmark-rule', execute: false }, iterations: 50 };
  await writeFile(input, JSON.stringify(body));
  // Exercise the real HTTP transport with proportional deadlines, without taking 25 seconds.
  const response = DesktopClient.prototype.response;
  t.mock.method(DesktopClient.prototype, 'response', function (this: DesktopClient, path: string, requestBody?: unknown, options: DesktopRequestOptions = {}) {
    return response.call(this, path, requestBody, { ...options, timeoutMillis: (options.timeoutMillis ?? 20_000) / 10 });
  });
  const server = createServer(async (req, res) => {
    assert.equal(req.url, '/debug/android/benchmark');
    assert.equal(req.headers.authorization, 'Bearer test-token');
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(Buffer.from(chunk));
    assert.deepEqual(JSON.parse(Buffer.concat(chunks).toString()), body);
    setTimeout(() => res.end(JSON.stringify({ samples: [{ nanos: 25_000_000_000 }] })), 2500);
  });
  try {
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    assert.deepEqual(await matchingDebug({
      port: (server.address() as { port: number }).port, duration: 20_000, operation: 'android-benchmark', input,
    }, 'test-token'), { samples: [{ nanos: 25_000_000_000 }] });
  } finally {
    server.closeAllConnections();
    await new Promise<void>(resolve => server.close(() => resolve()));
    assertDesktopTestPath(directory);
    await rm(directory, { recursive: true, force: true });
  }
});
