import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { repositoryRoot } from '../lib/local-paths.ts';
import { withAutomationPaused, enableDebugAccessibility } from '../lib/device-automation.ts';
import { DesktopClient } from '../lib/desktop-client.ts';
import { request as httpRequest } from 'node:http';
import { randomUUID } from 'node:crypto';
import { checkAndroidQuerySafety } from './check-android-query-safety.ts';
import { checkDebugServiceStop } from './check-debug-service-stop.ts';
import { buildAndroidQueryFixture } from '../lib/android-fixture-build.ts';
import { installGkdDebug } from '../lib/device-test-apps.ts';
import { createAdb } from '../lib/device-adb.ts';
import { withInstallConfirmation } from '../lib/device-install.ts';
import { withExistingDebugConnection } from '../lib/device-debug-connection.ts';
import type { DebugReadiness } from '../lib/debug-readiness.ts';

type Options = {
  port: number; operation: string; session?: string; after?: string;
  rule?: string; duration: number; output?: string; input?: string; run?: string; install?: boolean;
  signal?: AbortSignal;
  openGkd?: boolean;
  trace?: string;
  detailed?: boolean;
  recordQueries?: boolean;
  recordSystem?: boolean;
  attach?: boolean;
};

async function request(base: string, path: string, token?: string, body?: unknown, timeoutMillis?: number) {
  return new DesktopClient(Number(new URL(base).port)).json(path.slice(1), body, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    timeoutMillis,
  });
}

export async function matchingDebug(options: Options, token?: string): Promise<unknown> {
  options.signal?.throwIfAborted();
  if (options.detailed && !options.recordSystem && !options.rule?.trim()) throw new Error('--detailed requires --rule or --record-system');
  if (options.recordQueries && !options.recordSystem && !options.rule?.trim()) throw new Error('--record-queries requires --rule');
  const base = `http://127.0.0.1:${options.port}`;
  const appPaths: Record<string, string> = {
    app: '/debug/app', catalog: '/debug/app/catalog',
    foreground: '/debug/android/foreground', capabilities: '/debug/capabilities', engine: '/debug/engine',
    readiness: '/debug/readiness',
  };
  if (appPaths[options.operation]) {
    const result = await request(base, appPaths[options.operation]!, token);
    if (options.output) await writeFile(options.output, JSON.stringify(result, null, 2));
    return result;
  }
  const sessionPath = `/debug/sessions/${encodeURIComponent(options.session ?? '')}`;
  if (!['start', 'capture', 'policy', 'benchmark', 'android-benchmark'].includes(options.operation) && !options.session) throw new Error('--session is required');
  switch (options.operation) {
    case 'capture': {
      if (!options.output) throw new Error('capture requires --output');
      const session = await matchingDebug({ ...options, operation: 'start' }, token) as { id: string };
      const connection = { ...options, session: session.id };
      const failures: unknown[] = [];
      try {
        if (options.input) await matchingDebug({ ...connection, operation: 'run' }, token);
        await matchingDebug({ ...connection, operation: 'watch' }, token);
      } catch (error) { failures.push(error); }
      // Export even when watching or stopping fails, and ignore an already aborted
      // capture signal during cleanup. A stopped session may still have cancelling runs.
      try { await matchingDebug({ ...connection, signal: undefined, operation: 'stop' }, token); }
      catch (error) { failures.push(error); }
      try {
        const deadline = Date.now() + 30_000;
        while (true) {
          const state = await request(base, `/debug/sessions/${encodeURIComponent(session.id)}`, token) as { trackingComplete: boolean };
          if (state.trackingComplete) break;
          if (Date.now() >= deadline) throw new Error('Diagnostic cleanup is still pending after 30s; exported runs do not confirm physical input release');
          await new Promise(resolve => setTimeout(resolve, 100));
        }
      } catch (error) { failures.push(error); }
      let exported: unknown;
      try {
        exported = await matchingDebug({ ...connection, signal: undefined, operation: 'export' }, token);
        process.stderr.write(`Diagnostic export: ${options.output}\n`);
      } catch (error) { failures.push(error); }
      if (failures.length) throw new AggregateError(failures, 'Diagnostic capture failed; see export path above if export succeeded');
      return exported;
    }
    case 'benchmark':
    case 'android-benchmark': {
      if (!options.input) throw new Error('--input must contain a MatchBenchmarkRequest JSON');
      const result = await request(base, options.operation === 'benchmark' ? '/debug/benchmark' : '/debug/android/benchmark', token,
        JSON.parse(await readFile(options.input, 'utf8')),
        // Android allows 120s of Binder queries; leave time to return that result.
        options.operation === 'android-benchmark' ? 125_000 : undefined);
      if (options.output) await writeFile(options.output, JSON.stringify(result, null, 2));
      return result;
    }
    case 'run': {
      if (!options.input) throw new Error('--input must contain a MatchDebugRunRequest JSON');
      return request(base, `${sessionPath}/runs`, token, JSON.parse(await readFile(options.input, 'utf8')));
    }
    case 'run-status':
    case 'cancel': {
      if (!options.run) throw new Error('--run is required');
      return request(base, `${sessionPath}/runs/${encodeURIComponent(options.run)}${options.operation === 'cancel' ? '/cancel' : ''}`,
        token, options.operation === 'cancel' ? {} : undefined);
    }
    case 'start': return request(base, '/debug/sessions', token, {
      ruleFilter: options.rule ?? null, detailed: options.detailed ?? false, recordQueries: options.recordQueries ?? false,
      ...(options.recordSystem ? { recordSystem: true } : {}),
    });
    case 'status': return request(base, sessionPath, token);
    case 'events': return request(base, `${sessionPath}/events?after=${Number(options.after ?? 0)}`, token);
    case 'stop': return request(base, `${sessionPath}/stop`, token, {});
    case 'policy': return request(base, '/debug/policy', token,
      options.input ? JSON.parse(await readFile(options.input, 'utf8')) : undefined);
    case 'export': {
      const result = await request(base, `${sessionPath}/export`, token, {});
      if (options.output) await writeFile(options.output, JSON.stringify(result, null, 2));
      return options.output ? { output: options.output } : result;
    }
    case 'watch': {
      await new Promise<void>((resolve, reject) => {
        const req = httpRequest(`${base}${sessionPath}/stream`, {
          signal: options.signal,
          headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Last-Event-ID': options.after ?? '0' },
        }, response => {
          if (response.statusCode !== 200) {
            response.resume(); reject(new Error(`SSE failed: ${response.statusCode}`)); return;
          }
          response.on('data', chunk => process.stdout.write(chunk));
          response.on('end', resolve);
          response.on('error', reject);
        });
        const timer = setTimeout(() => { resolve(); req.destroy(); }, options.duration);
        req.on('close', () => clearTimeout(timer));
        req.on('error', reject);
        req.end();
      });
      return { stopped: true };
    }
    default: throw new Error(`Unknown diagnostic operation: ${options.operation}`);
  }
}

/** Bounded device capture owns its forwarding and restores automation through the existing helper. */
export async function deviceMatchingDebug(serial: string, options: Options) {
  if (options.detailed && !options.recordSystem && !options.rule?.trim()) throw new Error('--detailed requires --rule or --record-system');
  if (options.recordQueries && !options.recordSystem && !options.rule?.trim()) throw new Error('--record-queries requires --rule');
  const benchmark = options.operation === 'android-benchmark';
  const serviceStop = options.operation === 'service-stop-check';
  const querySafety = options.operation === 'query-safety';
  if (options.attach && (options.install || options.openGkd || options.trace || benchmark || serviceStop || querySafety)) {
    throw new Error('--attach cannot install, launch a page, run a benchmark/service-stop check or change device preparation');
  }
  if (!options.attach && !['capture', 'android-benchmark', 'service-stop-check', 'query-safety', 'app', 'catalog', 'foreground', 'capabilities', 'engine', 'readiness'].includes(options.operation)) {
    throw new Error('Use --attach for operations on an existing diagnostic session');
  }
  if (benchmark && !options.input) throw new Error('--input must contain an AndroidBenchmarkRequest JSON');
  const rawAdb = createAdb(serial);
  const parent = join(repositoryRoot, '.temp', 'matching-debug');
  await mkdir(parent, { recursive: true });
  const folder = await mkdtemp(join(parent, 'capture-'));
  const component = 'li.songe.gkd.debug/li.gkd.app.service.DebugService';
  const cancellation = new AbortController();
  const adb = withInstallConfirmation(rawAdb, cancellation.signal);
  const interrupt = () => cancellation.abort(new Error('Device diagnostic interrupted; cleaning up this command'));
  process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
  options.signal?.addEventListener('abort', interrupt, { once: true });
  try {
    if (options.signal?.aborted) interrupt();
    cancellation.signal.throwIfAborted();
    if (options.attach) {
      return await withExistingDebugConnection(adb, cancellation.signal, async ({ port, token, pid }) => {
        process.stderr.write(`Attached to existing GKD process ${pid}; settings and debug transport are retained\n`);
        return matchingDebug({ ...options, port, signal: cancellation.signal,
          output: options.output ?? (options.operation === 'capture' ? join(folder, 'diagnostics.json') : undefined) }, token);
      });
    }
    const queryOutput = options.output ?? join(folder, 'query-safety.json');
    const queryFixture = querySafety ? await buildAndroidQueryFixture() : undefined;
    return await withAutomationPaused(adb, folder, async () => {
      let localPort: number | undefined;
      try {
        cancellation.signal.throwIfAborted();
        if (options.install) {
          console.log('GKD APK:', await installGkdDebug(adb));
        }
        if (benchmark || querySafety) await enableDebugAccessibility(adb);
        // force-stop does not call onDestroy; discard the previous transport's credentials
        // before waiting for this startup to publish its own ready file.
        await adb('shell', 'run-as', 'li.songe.gkd.debug', 'rm', '-f', 'files/private-store/app-debug.json');
        await adb('shell', 'am', 'start-foreground-service', '-n', component);
        let credentials: { token: string; port: number } | undefined;
        const deadline = Date.now() + 15_000;
        while (Date.now() < deadline) {
          cancellation.signal.throwIfAborted();
          try {
            credentials = JSON.parse((await adb('shell', 'run-as', 'li.songe.gkd.debug',
              'cat', 'files/private-store/app-debug.json')).toString());
            break;
          } catch {
            await new Promise(resolve => setTimeout(resolve, 200));
          }
        }
        if (!credentials) throw new Error('Debug transport did not become ready');
        localPort = Number((await adb('forward', 'tcp:0', `tcp:${credentials.port}`)).toString().trim());
        if (!Number.isInteger(localPort) || localPort <= 0) throw new Error('Invalid forwarded port');
        const unauthenticated = await new DesktopClient(localPort).response('health');
        if (unauthenticated.status !== 401) throw new Error('Debug transport must reject unauthenticated requests');
        if (benchmark || querySafety) {
          // Launching the target before onServiceConnected loses its window event;
          // with matching disabled there may be no later event to confirm the page.
          const until = Date.now() + 15_000;
          let effective = false;
          let readiness: DebugReadiness | undefined;
          while (Date.now() < until) {
            cancellation.signal.throwIfAborted();
            const state = await request(`http://127.0.0.1:${localPort}`, '/debug/readiness', credentials.token) as DebugReadiness;
            readiness = state;
            if (state.runtime.foreground?.serviceMode === 1 && state.runtime.foreground.serviceEffective) { effective = true; break; }
            await new Promise(resolve => setTimeout(resolve, 200));
          }
          if (!effective) throw new Error(`Requested service mode did not connect before launching the test surface: ${JSON.stringify(readiness)}`);
        }
        const connection = { ...options, port: localPort, signal: cancellation.signal };
        if (querySafety) return await checkAndroidQuerySafety(adb, localPort, credentials.token,
          queryOutput, cancellation.signal, queryFixture!);
        if (serviceStop) return await checkDebugServiceStop(adb, localPort, credentials.token,
          options.output ?? join(folder, 'service-stop.json'), cancellation.signal);
        if (benchmark) {
          if (options.openGkd) await adb('shell', 'am', 'start', '-n', 'li.songe.gkd.debug/li.gkd.app.MainActivity');
          const readyUntil = Date.now() + 15_000;
          let ready = false;
          let readiness: unknown;
          while (Date.now() < readyUntil) {
            cancellation.signal.throwIfAborted();
            const state = await request(`http://127.0.0.1:${localPort}`, '/debug/readiness', credentials.token) as DebugReadiness;
            readiness = state;
            if (state.checks.androidBenchmark.ready && state.runtime.foreground?.serviceMode === 1 &&
              (!options.openGkd || state.runtime.foreground.foreground.confirmed.appId === 'li.songe.gkd.debug')) {
              ready = true; break;
            }
            await new Promise(resolve => setTimeout(resolve, 200));
          }
          if (!ready) throw new Error(`Benchmark requires an unlocked screen, confirmed accessibility foreground and loaded app catalog: ${JSON.stringify(readiness)}`);
          const benchmark = () => matchingDebug({ ...connection, output: options.output ?? join(folder, 'benchmark.json') }, credentials.token);
          if (!options.trace) return await benchmark();
          const remoteTrace = `/data/misc/perfetto-traces/gkd-${randomUUID()}.perfetto-trace`;
          const tracePid = (await adb('shell', 'perfetto', '--background-wait', '-t', '40s', '-b', '16mb',
            '-o', remoteTrace, '-a', 'li.songe.gkd.debug', 'binder_driver', 'sched')).toString().trim();
          if (!/^\d+$/.test(tracePid)) throw new Error(`Unexpected Perfetto PID: ${tracePid}`);
          try { return await benchmark(); }
          finally {
            // Stop only this independently owned Perfetto session, then wait for its file flush.
            await adb('shell', `if [ -d /proc/${tracePid} ]; then kill -TERM ${tracePid}; fi`);
            let stopped = false;
            for (let attempt = 0; attempt < 50; attempt++) {
              if ((await adb('shell', `if [ -d /proc/${tracePid} ]; then echo running; fi`)).toString().trim() === '') {
                stopped = true; break;
              }
              await new Promise(resolve => setTimeout(resolve, 100));
            }
            if (!stopped) throw new Error(`Perfetto still flushing; trace retained at ${remoteTrace}`);
            await adb('pull', remoteTrace, options.trace);
            await adb('shell', 'rm', '-f', remoteTrace);
          }
        }
        if (['app', 'catalog', 'foreground', 'capabilities', 'engine', 'readiness'].includes(options.operation)) {
          return await matchingDebug(connection, credentials.token);
        }
        return await matchingDebug({
          ...connection, operation: 'capture',
          output: options.output ?? join(folder, 'diagnostics.json'),
        }, credentials.token);
      } finally {
        try {
          try { await adb('shell', 'am', 'stopservice', '-n', component); }
          catch (error) {
            // Some Android am implementations return -1 (255) after a successful stop.
            const result = error as { code?: number; stderr?: Buffer };
            if (result.code !== 255 || result.stderr?.toString().trim() !== 'Service stopped') throw error;
          }
        }
        finally { if (localPort) await adb('forward', '--remove', `tcp:${localPort}`); }
      }
    }, undefined, { accessibilityForDiagnostics: benchmark || querySafety });
  } finally {
    options.signal?.removeEventListener('abort', interrupt);
    process.off('SIGINT', interrupt); process.off('SIGTERM', interrupt);
  }
}
