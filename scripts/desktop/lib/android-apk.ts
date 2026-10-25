import { execFile } from 'node:child_process';
import { readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { repositoryRoot } from './local-paths.ts';

const exec = promisify(execFile);

export async function readAndroidSdk(): Promise<string> {
  const properties = await readFile(join(repositoryRoot, 'local.properties'), 'utf8');
  const sdk = properties.match(/^sdk\.dir=(.+)$/m)?.[1]?.trim().replaceAll('\\:', ':').replaceAll('\\\\', '\\');
  if (!sdk) throw new Error('Android SDK path is required');
  return sdk;
}

export function parseApkBadging(badging: string, expectedPackage: string) {
  const packages = [...badging.matchAll(/^package: name='([^']+)'/gm)].map(match => match[1]!);
  if (packages.length !== 1 || packages[0] !== expectedPackage) {
    throw new Error(`APK package does not match installation target: ${expectedPackage}`);
  }
  const labels = new Set<string>();
  for (const line of badging.split(/\r?\n/)) {
    // Literal manifest labels (the test fixture) have no application-label lines.
    const label = line.match(/^application-label(?:-[^:]+)?:'(.*)'$/)?.[1]
      ?? line.match(/^application: label='(.*)' icon='/)?.[1];
    if (label?.trim()) labels.add(label);
  }
  if (!labels.size) throw new Error(`APK has no readable application label: ${expectedPackage}`);
  return { packageName: packages[0], labels: [...labels] };
}

/** Read actual APK metadata; never infer the visible app name from its filename. */
export async function readApkIdentity(apk: string, expectedPackage: string): Promise<{
  packageName: string; labels: string[]; sizeBytes: number;
}> {
  const sdk = await readAndroidSdk();
  const [badging, file] = await Promise.all([
    exec(join(sdk, 'build-tools/37.0.0/aapt2.exe'), ['dump', 'badging', apk], {
      cwd: repositoryRoot, windowsHide: true, timeout: 30_000, maxBuffer: 4_000_000,
    }),
    stat(apk),
  ]);
  return { ...parseApkBadging(badging.stdout, expectedPackage), sizeBytes: file.size };
}
