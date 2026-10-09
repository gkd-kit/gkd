import { parseArgs } from 'node:util';
import { repositoryRoot as repo, desktopProfile } from './lib/local-paths.ts';
import { resolve } from 'node:path';
import { captureProfile } from './commands/capture-profile.ts';
import { DesktopClient, type SimulatedService, type ServiceEventType } from './lib/desktop-client.ts';

const { values, positionals } = parseArgs({ allowPositionals: true, options: {
  help: { type: 'boolean', short: 'h' }, output: { type: 'string' },
  serial: { type: 'string' }, apps: { type: 'string' },
  font: { type: 'string', default: '/system/fonts/MiSansVF.ttf' },
  install: { type: 'boolean' }, screens: { type: 'boolean' },
  port: { type: 'string', default: '17322' }, service: { type: 'string' },
  event: { type: 'string' }, attempt: { type: 'string' }, reason: { type: 'string' },
} });
try {
  const command = positionals[0];
  if (values.help || !command) {
    console.log(`pnpm app:tools capture-profile [--serial SERIAL] [--apps FILE] [--font DEVICE_PATH] [--install] [--screens] [--output DIR]
pnpm app:tools services [--port 17322]
pnpm app:tools service-start|service-stop --service Activity [--port 17322]
pnpm app:tools service-event --service Activity --event Connected --attempt 1 [--reason TEXT] [--port 17322]

Service commands require an isolated Desktop (--test). Services: Status, Button, Activity, Event, Track, Screenshot, Http, Accessibility, Automation.
Events: Authorized, Cancelled, Connected, Failed. Use the attempt returned by services.

Capture requires adb and a built gkdDebug APK. It restores automation settings in finally.
If recovery fails, keep automation-recovery.json and restore its two fields before retrying.`);
  } else if (positionals.length !== 1) throw new Error('Expected one command');
  else if (command === 'capture-profile') {
    await captureProfile({ serial: values.serial, apps: values.apps, font: values.font,
      install: !!values.install, screens: !!values.screens,
      output: resolve(values.output ?? desktopProfile) }, repo);
  } else if (['services', 'service-start', 'service-stop', 'service-event'].includes(command)) {
    const port = Number(values.port);
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
