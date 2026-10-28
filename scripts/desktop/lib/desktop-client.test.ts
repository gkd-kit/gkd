import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';
import { DesktopClient } from './desktop-client.ts';

test('JSON and binary requests preserve authentication, raw input, content type and timeout options', async () => {
  const bytes = Buffer.from([0, 0xff, 0x80, 10]);
  const server = createServer(async (req, res) => {
    if (req.headers.authorization !== 'Bearer test-token') { res.writeHead(401); res.end('Missing credential'); return; }
    if (req.url === '/timeout') { req.resume(); return; }
    if (req.url === '/bytes') { res.end(bytes); return; }
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(Buffer.from(chunk));
    res.end(JSON.stringify({ method: req.method, type: req.headers['content-type'], body: Buffer.concat(chunks).toString() }));
  });
  try {
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
    const client = new DesktopClient((server.address() as { port: number }).port);
    const options = { headers: { Authorization: 'Bearer test-token' } };
    assert.deepEqual(await client.json('echo', undefined, {
      ...options, rawBody: 'literal=value', contentType: 'text/plain',
    }), { method: 'POST', type: 'text/plain', body: 'literal=value' });
    assert.deepEqual(await client.bytes('bytes', undefined, options), bytes);
    await assert.rejects(client.json('echo'), /HTTP 401: Missing credential/);
    await assert.rejects(client.json('timeout', undefined, { ...options, timeoutMillis: 20 }), /Request timeout: timeout/);
  } finally {
    server.closeAllConnections();
    await new Promise<void>(resolve => server.close(() => resolve()));
  }
});
