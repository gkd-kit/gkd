import { DesktopClient } from './desktop-client.ts';
import type { Adb } from './device-automation.ts';
import { setTimeout as delay } from 'node:timers/promises';

const packageName = 'li.songe.gkd.debug';

/** Attach preserves the running app, its settings and other clients' diagnostic sessions. */
export async function withExistingDebugConnection<T>(adb: Adb, signal: AbortSignal,
  work: (connection: { port: number; token: string; pid: number }) => Promise<T>): Promise<T> {
  async function pid() {
    const value = (await adb('shell', `pidof ${packageName} || true`)).toString().trim();
    if (!/^\d+$/.test(value)) throw new Error('No running GKD Debug app process; open the app before using --attach');
    return Number(value);
  }
  signal.throwIfAborted();
  const originalPid = await pid();
  // onStartCommand is idempotent. Existing transports keep their token and sessions.
  // Never delete credentials or stop the shared transport in attach mode.
  await adb('shell', 'am', 'start-foreground-service', '-n', `${packageName}/li.gkd.app.service.DebugService`);
  const checkProcess = async () => {
    if (await pid() !== originalPid) throw new Error('GKD process changed during attach; the original scene is no longer available');
  };
  let port: number | undefined;
  let workFailure: unknown;
  try {
    await checkProcess();
    const deadline = Date.now() + 15_000;
    let lastReason = 'credentials_unavailable';
    while (Date.now() < deadline) {
      signal.throwIfAborted();
      let credentials: { port: number; token: string } | undefined;
      try {
        const value = JSON.parse((await adb('shell', 'run-as', packageName,
          'cat', 'files/private-store/app-debug.json')).toString());
        if (Number.isInteger(value.port) && value.port > 0 && value.port <= 65535 &&
            typeof value.token === 'string' && /^[0-9a-f]{64}$/.test(value.token)) credentials = value;
        else lastReason = 'credentials_invalid';
      } catch {
        // JSON parse errors can include credential contents: do not log or retain them.
        lastReason = 'credentials_unavailable';
      }
      if (credentials) {
        const forwarded = (await adb('forward', 'tcp:0', `tcp:${credentials.port}`)).toString().trim();
        if (!/^\d+$/.test(forwarded) || Number(forwarded) <= 0 || Number(forwarded) > 65535) throw new Error('Invalid forwarded port');
        port = Number(forwarded);
        const client = new DesktopClient(port);
        let connected = false;
        try {
          const health = await client.response('health', undefined, {
            headers: { Authorization: `Bearer ${credentials.token}` }, timeoutMillis: 1000,
          });
          connected = health.status === 200 && JSON.parse(health.body.toString()).status === 'ready';
          lastReason = `health_http_${health.status}`;
        } catch { lastReason = 'transport_unavailable'; }
        if (connected) {
          if ((await client.response('health', undefined, { timeoutMillis: 1000 })).status !== 401) {
            throw new Error('Debug transport must reject unauthenticated requests');
          }
          await checkProcess();
          signal.throwIfAborted();
          const result = await work({ port, token: credentials.token, pid: originalPid });
          await checkProcess();
          return result;
        }
        await adb('forward', '--remove', `tcp:${port}`);
        port = undefined;
      }
      await delay(200, undefined, { signal });
      await checkProcess();
    }
    throw new Error(`Debug transport did not become ready: ${lastReason}; --attach does not reinstall or restart the app`);
  } catch (error) { workFailure = error; throw error; }
  finally {
    if (port !== undefined) {
      try { await adb('forward', '--remove', `tcp:${port}`); }
      catch (error) {
        if (workFailure) throw new AggregateError([workFailure, error], 'Attach failed and its ADB forward could not be removed');
        throw error;
      }
    }
  }
}
