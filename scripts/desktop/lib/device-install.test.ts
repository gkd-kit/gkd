import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { test } from 'node:test';
import type { Adb } from './device-automation.ts';
import { activeInstallSessions, installConfirmationTarget, installWithConfirmation, withInstallConfirmation } from './device-install.ts';
import { assertDesktopTestPath, desktopTests } from './local-paths.ts';

const packageName = 'li.gkd.a11y.fixture';
const identity = { packageName, labels: ['GKD accessibility fixture'], sizeBytes: 16588 };
const component = 'com.miui.securitycenter/com.miui.permcenter.install.AdbInstallActivity';
// Reduced from the Android 13 MIUI dump: the positive button really is button2.
const activity = `topResumedActivity=ActivityRecord{activity u0 ${component}} t1}
  * Hist #0: ActivityRecord{activity u0 ${component}}
    launchedFromUid=1000 launchedFromPackage=pm launchedFromFeature=null userId=0
    baseDir=/product/priv-app/MIUISecurityCenter/MIUISecurityCenter.apk
  mCurrentFocus=Window{window u0 ${component}}
`;
const emptySessions = 'Active install sessions:\n  \nFinalized install sessions:\n  \nHistorical install sessions:\n';
const session = (id = '123', pkg = packageName) => `  Active Session ${id}:
    userId=0 mOriginalInstallerUid=2000 mInstallerUid=2000
    sizeBytes=16588 appPackageName=${pkg} appLabel=null
    mCommitted=true mDestroyed=false
`;
const sessions = (entries = session()) => `Active install sessions:\n${entries}\nFinalized install sessions:\n\nHistorical install sessions:\n  Session 999:\n    appPackageName=unrelated.package\n`;
const node = (id: string, text: string, extra = '') => `<node package="com.miui.securitycenter" resource-id="${id}" text="${text}" ${extra}/>`;
const positive = node('android:id/button2', '继续安装', 'clickable="true" enabled="true" bounds="[101,2159][523,2299]"');
const xml = `<hierarchy rotation="0">${node('com.miui.securitycenter:id/alertTitle', 'USB安装提示')}
${node('com.miui.securitycenter:id/name', 'GKD accessibility fixture')}
${node('com.miui.securitycenter:id/desc', '正在通过USB安装此应用，是否继续？')}
${positive}${node('android:id/button1', '拒绝（7）', 'clickable="true" enabled="true" bounds="[556,2159][979,2299]"')}</hierarchy>`;

test('MIUI dialog recognition identifies the target label and actual continue button, never the rejection button', () => {
  assert.deepEqual(installConfirmationTarget(xml, identity), { label: identity.labels[0], button: '继续安装', x: 312, y: 2229 });
  for (const invalid of [
    xml.replace('GKD accessibility fixture', 'Other app'),
    xml.replace('text="继续安装"', 'text="拒绝"'),
    xml.replace('clickable="true" enabled="true"', 'clickable="true" enabled="false"'),
    xml.replace('clickable="true" enabled="true"', 'clickable="false" enabled="true"'),
    xml.replace('[101,2159][523,2299]', '[0,0][0,0]'),
    xml.replace('</hierarchy>', `${positive}</hierarchy>`),
    xml.replaceAll('com.miui.securitycenter', 'unrelated.package'),
  ]) assert.equal(installConfirmationTarget(invalid, identity), null);
});

test('install session parsing separates active shell sessions from finalized and historical records', () => {
  assert.deepEqual(activeInstallSessions(emptySessions), []);
  assert.deepEqual(activeInstallSessions(sessions()), [{ id: '123', packageName, sizeBytes: 16588, shell: true, committed: true }]);
  assert.equal(activeInstallSessions(sessions(session() + session('124', 'another.app'))).length, 2);
  assert.equal(activeInstallSessions(sessions(session().replace('mInstallerUid=2000', 'mInstallerUid=10001')))[0]?.shell, false);
  assert.throws(() => activeInstallSessions('Permission denied'), /Unknown/);
});

type Scenario = {
  activity?: string; finalActivity?: string; sessions?: string; baseline?: string; xml?: string;
  finishDuring?: 'dump' | 'xml'; cancelDuringXml?: boolean; tapError?: boolean; dumpError?: boolean; rejected?: boolean;
};

async function exercise(scenario: Scenario = {}) {
  await mkdir(desktopTests, { recursive: true });
  const directory = await mkdtemp(join(desktopTests, 'install-confirmation-'));
  const calls: string[][] = [];
  const abort = new AbortController();
  let resolveInstall!: (value: Buffer) => void;
  let rejectInstall!: (error: Error) => void;
  let activityReads = 0, sessionReads = 0;
  const pending = new Promise<Buffer>((resolve, reject) => { resolveInstall = resolve; rejectInstall = reject; });
  const rejection = new Error('INSTALL_FAILED_USER_RESTRICTED');
  const adb: Adb = async (...args) => {
    calls.push(args);
    if (args[0] === 'install') {
      assert.deepEqual(args, ['install', '-r', '-t', '--pkg', packageName, 'fixture.apk']);
      return pending;
    }
    if (args.slice(0, 4).join(' ') === 'shell dumpsys package installs') {
      return Buffer.from(sessionReads++ === 0 ? scenario.baseline ?? emptySessions : scenario.sessions ?? sessions());
    }
    if (args.slice(0, 4).join(' ') === 'shell dumpsys activity activities') {
      return Buffer.from(activityReads++ === 0 ? scenario.activity ?? activity : scenario.finalActivity ?? scenario.activity ?? activity);
    }
    if (args[1] === 'uiautomator') {
      if (scenario.dumpError) { rejectInstall(rejection); throw new Error('Lost response after hierarchy was written'); }
      if (scenario.finishDuring === 'dump') resolveInstall(Buffer.from('Success'));
      return Buffer.from('UI hierarchy dumped');
    }
    if (args[1] === 'cat') {
      if (scenario.finishDuring === 'xml') resolveInstall(Buffer.from('Success'));
      if (scenario.cancelDuringXml) { abort.abort(new Error('User cancelled')); resolveInstall(Buffer.from('Success')); }
      return Buffer.from(scenario.xml ?? xml);
    }
    if (args[1] === 'input') {
      assert.deepEqual(args, ['shell', 'input', 'tap', '312', '2229']);
      if (scenario.tapError) { rejectInstall(rejection); throw new Error('Input injection denied'); }
      if (scenario.rejected) rejectInstall(rejection); else resolveInstall(Buffer.from('Success'));
      return Buffer.alloc(0);
    }
    if (args[1] === 'rm') return Buffer.alloc(0);
    throw new Error(`Unexpected adb command: ${JSON.stringify(args)}`);
  };
  // Invalid dialogs remain unconfirmed and eventually return the original installation rejection.
  const timer = setTimeout(() => rejectInstall(rejection), 150);
  try {
    const result = await installWithConfirmation(adb, 'fixture.apk', identity, directory, abort.signal)
      .then(value => ({ value }), error => ({ error: error as Error }));
    const report = JSON.parse(await readFile(join(directory, 'confirmation.json'), 'utf8'));
    return { result, report, calls, rejection };
  } finally {
    clearTimeout(timer);
    assertDesktopTestPath(directory);
    await rm(directory, { recursive: true, force: true });
  }
}

test('pending install confirms once after matching the package session, UI label and foreground again', async () => {
  const { result, report, calls } = await exercise();
  assert.ok('value' in result);
  assert.equal(report.clicked, true);
  assert.equal(report.session.packageName, packageName);
  assert.equal(calls.filter(args => args[1] === 'input').length, 1);
  assert.equal(calls.filter(args => args[1] === 'rm').length, 1);
});

test('wrong or ambiguous applications, sessions, buttons and changed foreground cannot be confirmed', async () => {
  await Promise.all([
    { activity: activity.replaceAll(component, 'unrelated.app/.Screen') },
    { activity: activity.replace('launchedFromUid=1000', 'launchedFromUid=10001') },
    { finalActivity: activity.replace('Window{window ', 'Window{replacement ') },
    { sessions: sessions(session('123', 'another.app')) },
    { sessions: sessions(session() + session('124')) },
    { sessions: sessions().replace('sizeBytes=16588', 'sizeBytes=8') },
    { baseline: sessions() },
    { xml: xml.replace('GKD accessibility fixture', 'Other app') },
    { xml: xml.replace('enabled="true"', 'enabled="false"') },
    { xml: xml.replace('</hierarchy>', `${positive}</hierarchy>`) },
  ].map(async scenario => {
    const { result, report, calls, rejection } = await exercise(scenario);
    assert.ok('error' in result && result.error === rejection);
    assert.equal(report.clicked, false);
    assert.equal(calls.filter(args => args[1] === 'input').length, 0);
  }));
});

test('completion or cancellation while the UI is being read prevents a late click', async () => {
  for (const scenario of [{ finishDuring: 'dump' as const }, { finishDuring: 'xml' as const }, { cancelDuringXml: true }]) {
    const { result, report, calls } = await exercise(scenario);
    if ('cancelDuringXml' in scenario) assert.ok('error' in result && result.error.message === 'User cancelled');
    else assert.ok('value' in result);
    assert.equal(report.clicked, false);
    assert.equal(calls.filter(args => args[1] === 'input').length, 0);
  }
});

test('a rejected installation remains failed even after a click or a denied injection', async () => {
  for (const scenario of [{ rejected: true }, { tapError: true }]) {
    const { result, report, calls, rejection } = await exercise(scenario);
    assert.ok('error' in result && result.error === rejection);
    assert.match(report.error, /INSTALL_FAILED_USER_RESTRICTED/);
    assert.equal(calls.filter(args => args[1] === 'input').length, 1);
    if ('tapError' in scenario) assert.match(report.observerError, /Input injection denied/);
  }
});

test('a lost hierarchy dump response still cleans the attempted file without confirming or replacing install failure', async () => {
  const { result, report, calls, rejection } = await exercise({ dumpError: true });
  assert.ok('error' in result && result.error === rejection);
  assert.equal(report.clicked, false);
  assert.match(report.observerError, /Lost response after hierarchy was written/);
  assert.match(report.error, /INSTALL_FAILED_USER_RESTRICTED/);
  assert.equal(calls.filter(args => args[1] === 'input').length, 0);
  const dump = calls.find(args => args[1] === 'uiautomator')!;
  const removals = calls.filter(args => args[1] === 'rm');
  assert.deepEqual(removals, [['shell', 'rm', '-f', dump[3]!]]);
});

test('confirmation adapter passes non-install commands through without reading metadata or UI', async () => {
  const calls: string[][] = [];
  const adb = withInstallConfirmation(async (...args) => { calls.push(args); return Buffer.from('ok'); });
  assert.equal((await adb('uninstall', packageName)).toString(), 'ok');
  assert.equal((await adb('shell', 'pm', 'list', 'packages', packageName)).toString(), 'ok');
  assert.deepEqual(calls, [['uninstall', packageName], ['shell', 'pm', 'list', 'packages', packageName]]);
});
