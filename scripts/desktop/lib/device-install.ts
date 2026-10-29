import { mkdir, mkdtemp, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { randomUUID } from 'node:crypto';
import type { Adb } from './device-automation.ts';
import { readApkIdentity } from './android-apk.ts';
import { repositoryRoot } from './local-paths.ts';

const installerPackage = 'com.miui.securitycenter';
const installerActivity = `${installerPackage}/com.miui.permcenter.install.AdbInstallActivity`;
type Identity = { packageName: string; labels: string[]; sizeBytes: number };
type Session = { id: string; packageName: string | null; sizeBytes: number; shell: boolean; committed: boolean };

export function activeInstallSessions(dump: string): Session[] {
  const section = dump.match(/^Active install sessions:\s*\n([\s\S]*?)(?=^\S|$(?![\s\S]))/m)?.[1];
  if (section === undefined) throw new Error('Unknown package installer session dump');
  return [...section.matchAll(/Active Session (\d+):([\s\S]*?)(?=\s+Active Session \d+:|$)/g)].map(match => ({
    id: match[1]!, packageName: match[2]!.match(/\bappPackageName=(\S+)/)?.[1] ?? null,
    sizeBytes: Number(match[2]!.match(/\bsizeBytes=(\d+)/)?.[1]),
    shell: /\bmInstallerUid=2000\b/.test(match[2]!), committed: /\bmCommitted=true\b/.test(match[2]!),
  }));
}

function focusedInstaller(dump: string) {
  const focus = dump.match(/mCurrentFocus=Window\{(\S+) u0 (\S+)\}/);
  const resumed = dump.match(/topResumedActivity=ActivityRecord\{(\S+) u0 (\S+)\}/);
  if (focus?.[2] !== installerActivity || resumed?.[2] !== installerActivity) return null;
  const record = dump.slice(dump.indexOf(`* Hist`), dump.indexOf('baseDir='));
  if (!/launchedFromUid=1000 launchedFromPackage=pm\b/.test(record)) return null;
  return `${focus[1]}/${resumed[1]}`;
}

function decodeXml(value: string) {
  return value.replace(/&(#x[\da-f]+|#\d+|quot|apos|lt|gt|amp);/gi, (_, entity: string) => {
    if (entity.startsWith('#')) return String.fromCodePoint(Number.parseInt(entity.slice(entity[1] === 'x' ? 2 : 1), entity[1] === 'x' ? 16 : 10));
    return ({ quot: '"', apos: "'", lt: '<', gt: '>', amp: '&' } as Record<string, string>)[entity]!;
  });
}

/** Recognize the observed MIUI USB dialog, including its reversed positive/negative button IDs. */
export function installConfirmationTarget(xml: string, identity: Identity) {
  if (!xml.includes('<hierarchy') || !xml.includes('</hierarchy>')) return null;
  const nodes = [...xml.matchAll(/<node\b([^>]*)>/g)].map(match => Object.fromEntries(
    [...match[1]!.matchAll(/([\w:-]+)="([^"]*)"/g)].map(attr => [attr[1]!, decodeXml(attr[2]!)])));
  const one = (id: string) => { const found = nodes.filter(node => node['resource-id'] === id && node.package === installerPackage); return found.length === 1 ? found[0] : undefined; };
  const title = one(`${installerPackage}:id/alertTitle`), name = one(`${installerPackage}:id/name`);
  const description = one(`${installerPackage}:id/desc`), button = one('android:id/button2');
  if (title?.text !== 'USB安装提示' || !name?.text || !identity.labels.includes(name.text) ||
    description?.text !== '正在通过USB安装此应用，是否继续？' || button?.text !== '继续安装' ||
    button.clickable !== 'true' || button.enabled !== 'true') return null;
  const bounds = button.bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/)?.slice(1).map(Number);
  if (!bounds || bounds[2]! <= bounds[0]! || bounds[3]! <= bounds[1]!) return null;
  return { label: name.text, button: button.text, x: Math.floor((bounds[0]! + bounds[2]!) / 2), y: Math.floor((bounds[1]! + bounds[3]!) / 2) };
}

/** Only the pending install owns this observer. No clicks survive its completion or cancellation. */
export async function installWithConfirmation(adb: Adb, apk: string, identity: Identity, directory: string, signal?: AbortSignal) {
  await mkdir(directory, { recursive: true });
  const report: Record<string, unknown> = { target: identity, clicked: false };
  const xmlPath = `/data/local/tmp/gkd-install-${randomUUID()}.xml`;
  let done = false, xmlAttempted = false;
  let baseline: Session[] | undefined;
  try { baseline = activeInstallSessions((await adb('shell', 'dumpsys', 'package', 'installs')).toString()); }
  catch (error) { report.observerError = String(error); }
  signal?.throwIfAborted();
  const installing = adb('install', '-r', '-t', '--pkg', identity.packageName, apk)
    .then(value => { done = true; return { value }; }, error => { done = true; return { error }; });
  const active = () => !done && !signal?.aborted;
  try {
    if (baseline?.length === 0) {
      while (active()) {
        const activity = (await adb('shell', 'dumpsys', 'activity', 'activities')).toString();
        if (!active()) break;
        const focus = focusedInstaller(activity);
        if (!focus) { await Promise.race([pause(250), installing]); continue; }
        const sessions = activeInstallSessions((await adb('shell', 'dumpsys', 'package', 'installs')).toString());
        const session = sessions.length === 1 ? sessions[0] : undefined;
        if (!active()) break;
        if (!session || session.packageName !== identity.packageName || session.sizeBytes !== identity.sizeBytes || !session.shell || !session.committed) {
          report.reason = 'install_session_mismatch'; break;
        }
        xmlAttempted = true;
        await adb('shell', 'uiautomator', 'dump', xmlPath);
        if (!active()) break;
        const xml = (await adb('shell', 'cat', xmlPath)).toString();
        if (!active()) break;
        const target = installConfirmationTarget(xml, identity);
        await writeFile(join(directory, 'prompt.xml'), xml);
        if (!target) { report.reason = 'unrecognized_install_dialog'; break; }
        const [latestActivity, latestInstalls] = await Promise.all([
          adb('shell', 'dumpsys', 'activity', 'activities'), adb('shell', 'dumpsys', 'package', 'installs'),
        ]);
        if (!active()) break;
        const latest = activeInstallSessions(latestInstalls.toString());
        if (focusedInstaller(latestActivity.toString()) !== focus || latest.length !== 1 || JSON.stringify(latest[0]) !== JSON.stringify(session)) {
          report.reason = 'install_dialog_changed'; break;
        }
        report.session = session; report.confirmation = target;
        await adb('shell', 'input', 'tap', String(target.x), String(target.y));
        report.clicked = true;
        break;
      }
    } else if (baseline) report.reason = 'preexisting_install_session';
  } catch (error) { report.observerError = String(error); }
  // Observation errors never turn a failed install into success, nor discard the original install result.
  const result = await installing;
  try {
    if ('error' in result) { report.error = String(result.error); throw result.error; }
    report.installed = true;
    signal?.throwIfAborted();
    return result.value;
  } finally {
    try { if (xmlAttempted) await adb('shell', 'rm', '-f', xmlPath); }
    finally {
      await writeFile(join(directory, 'confirmation.json'), JSON.stringify(report, null, 2));
      console.log(`Installation confirmation: ${join(directory, 'confirmation.json')}`);
    }
  }
}

/** Add confirmation to actual installs only; unchanged APKs never enter this adapter. */
export function withInstallConfirmation(adb: Adb, signal?: AbortSignal): Adb {
  return async (...args) => {
    if (args[0] !== 'install') return adb(...args);
    if (args.length !== 6 || args[1] !== '-r' || args[2] !== '-t' || args[3] !== '--pkg') throw new Error('Expected an explicit target package for installation');
    const identity = await readApkIdentity(args[5]!, args[4]!);
    const parent = join(repositoryRoot, '.temp', 'install-confirmation');
    await mkdir(parent, { recursive: true });
    return installWithConfirmation(adb, args[5]!, identity, await mkdtemp(join(parent, 'attempt-')), signal);
  };
}
