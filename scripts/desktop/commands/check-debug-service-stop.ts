import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { basename, dirname, join } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { DesktopClient } from '../lib/desktop-client.ts';
import type { Adb } from '../lib/device-automation.ts';

const packageName = 'li.songe.gkd.debug';
const component = `${packageName}/li.gkd.app.service.DebugService`;
const credentialPath = 'files/private-store/app-debug.json';
type UiNode = { attrs: Record<string, string>; children: UiNode[] };
type NotificationTarget = { kind: 'stop' | 'expand' | 'expand-long-press'; x: number; y: number };

function descendants(node: UiNode): UiNode[] { return [node, ...node.children.flatMap(descendants)]; }
function nodeBounds(node: UiNode) {
  const bounds = node.attrs.bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/)?.slice(1).map(Number);
  return bounds && bounds[2]! > bounds[0]! && bounds[3]! > bounds[1]! ? bounds : null;
}
function tapTarget(node: UiNode, kind: NotificationTarget['kind']) {
  const attrs = node.attrs;
  if (attrs.package !== 'com.android.systemui' || attrs.clickable !== 'true' || attrs.enabled !== 'true') return null;
  const bounds = nodeBounds(node);
  if (!bounds) return null;
  return { kind, x: Math.floor((bounds[0]! + bounds[2]!) / 2), y: Math.floor((bounds[1]! + bounds[3]!) / 2) };
}

/** Only operate on the uniquely identified debug notification, never another service's Stop button. */
export function debugNotificationTarget(xml: string): NotificationTarget | null {
  if (!xml.includes('<hierarchy') || !xml.includes('</hierarchy>')) return null;
  const roots: UiNode[] = [], stack: UiNode[] = [];
  for (const match of xml.matchAll(/<\/?node\b[^>]*>/g)) {
    const tag = match[0];
    if (tag.startsWith('</')) { stack.pop(); continue; }
    const node: UiNode = { attrs: Object.fromEntries([...tag.matchAll(/([\w:-]+)="([^"]*)"/g)].map(attr => [attr[1]!, attr[2]!])), children: [] };
    (stack.at(-1)?.children ?? roots).push(node);
    if (!tag.endsWith('/>')) stack.push(node);
  }
  const all = roots.flatMap(descendants);
  const titles = all.filter(n => n.attrs.package === 'com.android.systemui' && n.attrs.text === '调试服务已开启');
  if (titles.length !== 1) return null;
  const candidates = all.flatMap(row => {
    const nodes = descendants(row);
    if (!nodes.includes(titles[0]!)) return [];
    // Do not associate a neighbouring notification's action with this title.
    if (nodes.some(n => /:id\/(title|notification_title)$/.test(n.attrs['resource-id'] ?? '') && n !== titles[0])) return [];
    return nodes.flatMap(node => {
      const attrs = node.attrs;
      const id = attrs['resource-id'] ?? '';
      const kind = /:id\/action\d+$/.test(id) && attrs.text === '停止' ? 'stop'
        : /:id\/expand_button$/.test(id) ? 'expand' : null;
      const target = kind && tapTarget(node, kind);
      return target ? [{ node, size: nodes.length, ...target }] : [];
    });
  });
  const stop = candidates.filter(c => c.kind === 'stop');
  const available = stop.length ? stop : candidates;
  if (!available.length) {
    // MIUI exposes the single notification's actions after a long press on its visible card.
    const rows = all.filter(n => n.children.some(c => c.attrs['resource-id'] === 'com.android.systemui:id/expanded') &&
      descendants(n).includes(titles[0]!) && descendants(n).some(c => c.attrs['resource-id'] === 'com.android.systemui:id/app_icon' && c.attrs['content-desc'] === 'GKD-debug'));
    if (rows.length === 1) {
      return tapTarget(rows[0]!, 'expand-long-press');
    }
    // MIUI folds multiple app notifications into a clickable group with no separate expand button.
    const groups = all.filter(n => n.attrs['resource-id'] === 'com.android.systemui:id/notification_children_container' &&
      n.attrs.package === 'com.android.systemui' && n.attrs['content-desc'] === 'GKD-debug' && descendants(n).includes(titles[0]!) &&
      !descendants(n).some(c => c.attrs['resource-id'] === 'com.android.systemui:id/collapse_button'));
    if (groups.length !== 1) return null;
    const groupRows = all.filter(n => n.children.includes(groups[0]!));
    return groupRows.length === 1 ? tapTarget(groupRows[0]!, 'expand') : null;
  }
  const smallest = Math.min(...available.map(c => c.size));
  const closest = available.filter(c => c.size === smallest);
  const unique = [...new Map(closest.map(c => [c.node, c])).values()];
  if (unique.length !== 1) return null;
  const { kind, x, y } = unique[0]!;
  return { kind, x, y };
}

/** Verify notification stop, transport cleanup and restart in the same app process. */
export async function checkDebugServiceStop(adb: Adb, port: number, token: string, output: string, signal: AbortSignal) {
  await mkdir(dirname(output), { recursive: true });
  const client = new DesktopClient(port);
  const originalPid = (await adb('shell', 'pidof', packageName)).toString().trim();
  assert.match(originalPid, /^\d+$/);
  const report: Record<string, unknown> = { pid: Number(originalPid), stops: [], notificationSteps: [] };
  const devicePort = JSON.parse((await adb('shell', 'run-as', packageName, 'cat', credentialPath)).toString()).port as number;
  const browser = await client.response('api/getServerInfo', {});
  assert.equal(browser.status, 200);
  assert.equal(browser.headers['gkd-rpc-status'], 'ok');
  assert.equal((await client.response('debug/app')).status, 401);
  const diagnostic = await client.response('debug/app', undefined, { headers: { Authorization: `Bearer ${token}` } });
  assert.equal(diagnostic.status, 200);
  assert.equal(diagnostic.headers['gkd-rpc-status'], undefined);
  report.sharedListener = { devicePort, browserRpc: true, authenticatedDiagnostics: true };
  const stops = report.stops as unknown[];
  const sameProcess = async () => assert.equal((await adb('shell', 'pidof', packageName)).toString().trim(), originalPid, 'Stopping Debug must preserve the app process');
  async function stopped(kind: string) {
    const deadline = Date.now() + 8000;
    let credentialsRemoved = false, httpClosed = false, serviceStopped = false;
    do {
      signal.throwIfAborted();
      credentialsRemoved = (await adb('shell', `run-as ${packageName} sh -c 'if [ -f ${credentialPath} ]; then echo present; else echo absent; fi'`)).toString().trim() === 'absent';
      const services = (await adb('shell', 'dumpsys', 'activity', 'services', component)).toString();
      serviceStopped = !services.includes('ServiceRecord{');
      try {
        await client.response('health', undefined, { headers: { Authorization: `Bearer ${token}` }, timeoutMillis: 1000 });
        httpClosed = false;
      } catch (error) {
        httpClosed = ['ECONNRESET', 'ECONNREFUSED', 'EPIPE'].includes((error as NodeJS.ErrnoException).code ?? '');
      }
      if (credentialsRemoved && httpClosed && serviceStopped) break;
      await pause(100);
    } while (Date.now() < deadline);
    const evidence = { kind, credentialsRemoved, httpClosed, serviceStopped };
    stops.push(evidence);
    assert.ok(credentialsRemoved && httpClosed && serviceStopped, JSON.stringify(evidence));
    await sameProcess();
  }
  async function restart() {
    await adb('shell', 'am', 'start-foreground-service', '-n', component);
    const deadline = Date.now() + 15000;
    do {
      signal.throwIfAborted();
      let next: { token: string; port: number } | undefined;
      try { next = JSON.parse((await adb('shell', 'run-as', packageName, 'cat', credentialPath)).toString()); }
      catch { /* Startup has not published credentials yet. Never log credential contents. */ }
      if (next && next.port === devicePort && /^[0-9a-f]{64}$/.test(next.token)) {
        let ready = false;
        try { ready = (await client.response('health', undefined, { headers: { Authorization: `Bearer ${next.token}` }, timeoutMillis: 1000 })).status === 200; }
        catch { /* The restarted listener may still be binding. */ }
        if (ready) {
          assert.notEqual(next.token, token, 'A restarted transport must publish fresh credentials');
          token = next.token;
          await sameProcess();
          return token;
        }
      }
      await pause(100);
    } while (Date.now() < deadline);
    throw new Error('Debug service did not restart in the existing process');
  }
  const xmlPath = `/data/local/tmp/gkd-debug-notification-${randomUUID()}.xml`;
  try {
    // Opening the shade itself changes the foreground, so test the button while no action is active.
    await adb('shell', 'cmd', 'statusbar', 'expand-notifications');
    try {
      let clicked = false;
      for (let attempt = 0; attempt < 3; attempt++) {
        signal.throwIfAborted();
        await adb('shell', 'uiautomator', 'dump', xmlPath);
        const xml = (await adb('shell', 'cat', xmlPath)).toString();
        await writeFile(join(dirname(output), `${basename(output, '.json')}-notification-${attempt}.xml`), xml);
        await writeFile(join(dirname(output), `${basename(output, '.json')}-notification-${attempt}.png`), await adb('exec-out', 'screencap', '-p'));
        const target = debugNotificationTarget(xml);
        assert.ok(target, 'Cannot uniquely identify the Debug notification action; retained the UI dump without guessing coordinates');
        (report.notificationSteps as NotificationTarget[]).push(target);
        if (target.kind === 'expand-long-press') await adb('shell', 'input', 'swipe', String(target.x), String(target.y), String(target.x), String(target.y), '700');
        else await adb('shell', 'input', 'tap', String(target.x), String(target.y));
        if (target.kind === 'stop') { report.notificationAction = target; clicked = true; break; }
        await pause(250);
      }
      assert.ok(clicked, 'Debug notification did not expose its Stop action');
      await stopped('notification');
    } finally {
      try { await adb('shell', 'cmd', 'statusbar', 'collapse'); }
      finally { await adb('shell', 'rm', '-f', xmlPath); }
    }
    await restart();
    report.verified = true;
    return { output, verified: true, stops: stops.length };
  } catch (error) { report.error = String(error); throw error; }
  finally { await writeFile(output, JSON.stringify(report, null, 2)); }
}
