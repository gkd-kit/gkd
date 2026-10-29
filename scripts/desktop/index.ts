import { resolveDesktopPort } from './lib/desktop-connection.ts';
import { parseArgs } from 'node:util';
import { repositoryRoot as repo, desktopProfile } from './lib/local-paths.ts';
import { resolve } from 'node:path';
import { captureProfile } from './commands/capture-profile.ts';
import { DesktopClient, type SimulatedService, type ServiceEventType } from './lib/desktop-client.ts';
import { checkSubscriptionHttp } from './commands/check-subscription-http.ts';
import { matchingDebug, deviceMatchingDebug } from './commands/matching-debug.ts';
import { checkMatchingDebug } from './commands/check-matching-debug.ts';
import { deviceRecovery } from './commands/device-recovery.ts';
import { deviceTestApps } from './commands/device-test-apps.ts';
import { deviceInstall } from './commands/device-install.ts';
import { checkDiagnosticPerformance } from './commands/check-diagnostic-performance.ts';
import { checkDiagnosticPage } from './commands/check-diagnostic-page.ts';
import { summarizeMatching } from './commands/summarize-matching.ts';
import { summarizeBinder } from './commands/summarize-binder.ts';
import { collectExpressionCorpus } from './commands/expression-corpus.ts';

const { values, positionals } = parseArgs({ allowPositionals: true, options: {
  help: { type: 'boolean', short: 'h' }, output: { type: 'string' },
  serial: { type: 'string' }, apps: { type: 'string' },
  font: { type: 'string', default: '/system/fonts/MiSansVF.ttf' },
  install: { type: 'boolean' }, screens: { type: 'boolean' },
  'open-gkd': { type: 'boolean' },
  trace: { type: 'string' },
  'trace-processor': { type: 'string' },
  detailed: { type: 'boolean' },
  'record-queries': { type: 'boolean' },
  'record-system': { type: 'boolean' },
  attach: { type: 'boolean' },
  port: { type: 'string' }, service: { type: 'string' },
  event: { type: 'string' }, attempt: { type: 'string' }, reason: { type: 'string' },
  connection: { type: 'string' },
  operation: { type: 'string' }, session: { type: 'string' },
  after: { type: 'string' }, rule: { type: 'string' }, duration: { type: 'string', default: '20000' },
  input: { type: 'string' }, run: { type: 'string' },
} });
try {
  const command = positionals[0];
  if (values.help || !command) {
    console.log(`pnpm app-tools capture-profile [--serial SERIAL] [--apps FILE] [--font DEVICE_PATH] [--install] [--screens] [--output DIR]
pnpm app-tools services [--port 8888 | --connection FILE]
pnpm app-tools check-subscription-http [--port 8888 | --connection FILE]
pnpm app-tools check-matching-debug [--port 8888 | --connection FILE]
pnpm app-tools device-diagnostic-performance --serial SERIAL --apps APP_ID [--output .local/tests/diagnostic-performance]
pnpm app-tools device-diagnostic-page-check --serial SERIAL [--apps APP_ID] [--output .local/tests/diagnostic-page]
pnpm app-tools service-start|service-stop --service Activity [--port 8888 | --connection FILE]
pnpm app-tools service-event --service Activity --event Connected --attempt 1 [--reason TEXT] [--port 8888 | --connection FILE]
pnpm app-tools matching-debug --operation readiness|start|capture|status|events|watch|stop|export|policy|engine|benchmark|run|run-status|cancel [--session ID] [--run ID] [--input JSON] [--duration 20000] [--detailed --rule ID] [--record-queries --rule ID] [--record-system] [--after SEQUENCE] [--output FILE]
pnpm app-tools device-matching-debug --serial SERIAL [--attach | --install] [--operation capture|start|status|events|watch|stop|export|run|run-status|cancel] [--session ID] [--run ID] [--input JSON] [--duration 20000] [--output FILE] [--detailed --rule ID]
pnpm app-tools matching-summary --input FILE [--output FILE]
pnpm app-tools expression-corpus --input SUBSCRIPTIONS_DIR --output FILE
pnpm app-tools matching-binder-summary --input BENCHMARK_JSON --trace TRACE --output JSON [--trace-processor PATH]
pnpm app-tools device-app-debug --serial SERIAL --operation readiness|app|catalog|foreground|capabilities|engine [--attach | --install] [--output FILE]
pnpm app-tools device-install [--serial SERIAL]
pnpm app-tools device-recovery --serial SERIAL --operation status|restore
pnpm app-tools device-matching-benchmark --serial SERIAL --input JSON [--install] [--open-gkd] [--output FILE] [--trace FILE]
pnpm app-tools device-service-stop-check --serial SERIAL [--install] [--output FILE]
pnpm app-tools device-query-safety --serial SERIAL [--install] [--output FILE]
pnpm app-tools device-test-apps --serial SERIAL --operation status|cleanup

Service commands require an isolated Desktop (--test). Services: Status, Button, Activity, Event, Track, Screenshot, Debug, Accessibility, Automation.
Events: Authorized, Cancelled, Connected, Failed. Use the attempt returned by services.

Device commands require adb and an installed gkdDebug app; --install also needs a built APK and skips identical installed content. Controlled capture restores automation settings in finally.
device-install builds gkdDebug once and updates the specified device or all connected, authorized devices in sequence.
Query checks retain their test app across commands; finish a whole debugging round with device-test-apps --operation cleanup.
--attach requires an already running Debug app; it preserves settings, page and shared transport. Session operations require --attach.
If recovery fails, keep .local/device-recovery and use device-recovery after reconnecting.`);
  } else if (positionals.length !== 1) throw new Error('Expected one command');
  else if (command === 'expression-corpus') {
    if (!values.input || !values.output) throw new Error('--input and --output are required');
    console.log(await collectExpressionCorpus(values.input, values.output));
  }
  else if (command === 'matching-binder-summary') {
    if (!values.input || !values.trace || !values.output) throw new Error('--input, --trace and --output are required');
    console.log(JSON.stringify(await summarizeBinder(values.input, values.trace,
      values['trace-processor'] ?? resolve(repo, '.local/tools/trace_processor'), values.output), null, 2));
  }
  else if (command === 'matching-summary') {
    if (!values.input) throw new Error('--input is required');
    console.log(JSON.stringify(await summarizeMatching(values.input, values.output), null, 2));
  }
  else if (command === 'device-recovery') {
    if (!values.serial) throw new Error('--serial is required');
    console.log(JSON.stringify(await deviceRecovery(values.serial, values.operation ?? 'status'), null, 2));
  }
  else if (command === 'device-diagnostic-performance') {
    if (!values.serial || !values.apps) throw new Error('--serial and --apps are required');
    console.log(await checkDiagnosticPerformance(values.serial, values.apps, values.output ?? '.local/tests/diagnostic-performance'));
  }
  else if (command === 'device-diagnostic-page-check') {
    if (!values.serial) throw new Error('--serial is required');
    console.log(await checkDiagnosticPage(values.serial, values.output ?? '.local/tests/diagnostic-page', values.apps));
  }
  else if (command === 'device-install') {
    console.log(JSON.stringify(await deviceInstall(values.serial), null, 2));
  }
  else if (command === 'device-test-apps') {
    if (!values.serial) throw new Error('--serial is required');
    console.log(JSON.stringify(await deviceTestApps(values.serial, values.operation ?? 'status'), null, 2));
  }
  else if (command === 'check-matching-debug') {
    console.log(await checkMatchingDebug(new DesktopClient(await resolveDesktopPort(values.port, values.connection))));
  }
  else if (command === 'matching-debug' || command === 'device-matching-debug' || command === 'device-app-debug' || command === 'device-matching-benchmark' || command === 'device-service-stop-check' || command === 'device-query-safety') {
    const duration = Number(values.duration);
    const port = command === 'matching-debug' ? await resolveDesktopPort(values.port, values.connection) : 8888;
    if (!Number.isInteger(duration) || duration < 1 || duration > 600000) throw new Error('Invalid duration');
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid port');
    const options = { port, duration, operation: command === 'device-query-safety' ? 'query-safety' : command === 'device-matching-benchmark' ? 'android-benchmark' : command === 'device-service-stop-check' ? 'service-stop-check' :
      values.operation ?? (command === 'device-matching-debug' ? 'capture' : command === 'device-app-debug' ? 'readiness' : 'status'), session: values.session,
      after: values.after, rule: values.rule, detailed: values.detailed, recordQueries: values['record-queries'], recordSystem: values['record-system'], attach: values.attach, output: values.output, input: values.input, run: values.run, install: values.install, openGkd: values['open-gkd'], trace: values.trace };
    if (command !== 'matching-debug' && !values.serial) throw new Error('--serial is required');
    if (command === 'device-app-debug' && !['app', 'catalog', 'foreground', 'capabilities', 'engine', 'readiness'].includes(options.operation)) {
      throw new Error('Expected readiness, app, catalog, foreground, capabilities or engine');
    }
    if (command === 'matching-debug' && options.operation === 'capture') {
      const cancellation = new AbortController();
      const interrupt = () => cancellation.abort(new Error('Diagnostic interrupted; stopping and exporting'));
      process.on('SIGINT', interrupt); process.on('SIGTERM', interrupt);
      try { console.log(JSON.stringify(await matchingDebug({ ...options, signal: cancellation.signal }), null, 2)); }
      finally { process.off('SIGINT', interrupt); process.off('SIGTERM', interrupt); }
    } else {
      console.log(JSON.stringify(command !== 'matching-debug'
        ? await deviceMatchingDebug(values.serial!, options) : await matchingDebug(options), null, 2));
    }
  }
  else if (command === 'check-subscription-http') {
    const port = await resolveDesktopPort(values.port, values.connection);
    console.log(await checkSubscriptionHttp(new DesktopClient(port)));
  } else if (command === 'capture-profile') {
    await captureProfile({ serial: values.serial, apps: values.apps, font: values.font,
      install: !!values.install, screens: !!values.screens,
      output: resolve(values.output ?? desktopProfile) }, repo);
  } else if (['services', 'service-start', 'service-stop', 'service-event'].includes(command)) {
    const port = await resolveDesktopPort(values.port, values.connection);
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid port');
    const client = new DesktopClient(port);
    if (command === 'services') console.log(JSON.stringify(await client.services(), null, 2));
    else {
      if (!(await client.state()).isolated) throw new Error('Start Desktop with --test before changing services');
      if (!values.service) throw new Error('--service is required');
      const service = values.service as SimulatedService;
      if (command === 'service-event') {
        const attempt = Number(values.attempt);
        if (!values.event || values.attempt === undefined || !Number.isSafeInteger(attempt) || attempt < 0) {
          throw new Error('--event and a nonnegative integer --attempt are required');
        }
        console.log(JSON.stringify(await client.serviceEvent(service, values.event as ServiceEventType, attempt, values.reason), null, 2));
      } else console.log(JSON.stringify(await client.serviceCommand(service, command === 'service-start'), null, 2));
    }
  } else throw new Error(`Unknown command: ${command}`);
} catch (error) { console.error(error); process.exitCode = 1; }
