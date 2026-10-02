import { parseArgs } from 'node:util';
import { repositoryRoot as repo, desktopProfile } from './lib/local-paths.ts';
import { resolve } from 'node:path';
import { captureProfile } from './commands/capture-profile.ts';

const { values, positionals } = parseArgs({ allowPositionals: true, options: {
  help: { type: 'boolean', short: 'h' }, output: { type: 'string' },
  serial: { type: 'string' }, apps: { type: 'string' },
  font: { type: 'string', default: '/system/fonts/MiSansVF.ttf' },
  install: { type: 'boolean' }, screens: { type: 'boolean' },
} });
try {
  const command = positionals[0];
  if (values.help || !command) {
    console.log(`pnpm app:tools capture-profile [--serial SERIAL] [--apps FILE] [--font DEVICE_PATH] [--install] [--screens] [--output DIR]

Capture requires adb and a built gkdDebug APK. It restores automation settings in finally.
If recovery fails, keep automation-recovery.json and restore its two fields before retrying.`);
  } else if (positionals.length !== 1) throw new Error('Expected one command');
  else if (command === 'capture-profile') {
    await captureProfile({ serial: values.serial, apps: values.apps, font: values.font,
      install: !!values.install, screens: !!values.screens,
      output: resolve(values.output ?? desktopProfile) }, repo);
  } else throw new Error(`Unknown command: ${command}`);
} catch (error) { console.error(error); process.exitCode = 1; }
