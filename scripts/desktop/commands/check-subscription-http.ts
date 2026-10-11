import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { DesktopClient } from '../lib/desktop-client.ts';
import { assertDesktopTestPath } from '../lib/local-paths.ts';

interface Group { key: number; name: string }
interface Subscription {
  id: number; apps: { id: string; groups: Group[] }[]; globalGroups: Group[];
}
interface Configuration {
  subsItems: { id: number; enable: boolean }[];
  appConfigs: { subsId: number; appId: string; enable: boolean }[];
  appGroupConfigs: { subsId: number; appId: string; groupKey: number; enable: boolean | null }[];
  globalGroupConfigs: { subsId: number; groupKey: number; enable: boolean | null }[];
  categoryConfigs: { subsId: number; categoryKey: number; enable: boolean | null }[];
}
interface Rules { subscription: Subscription; configuration: Configuration }
interface RpcError { code: string; message: string; path: string | null; stackTrace: string | null }

/** Exercises the running production HTTP service; all writes require the isolated Desktop host. */
export async function checkSubscriptionHttp(desktop: DesktopClient, rpcPort: number) {
  assert.equal((await desktop.state()).isolated, true, 'Start Desktop with --test before running write checks');
  const catalog = await desktop.json<{ profileDirectory: string }>('app-catalog');
  const session = dirname(catalog.profileDirectory);
  assertDesktopTestPath(session);
  const reportFile = join(session, 'reports', 'subscription-http.json');
  const rpc = new DesktopClient(rpcPort);
  const calls: unknown[] = [];
  const checks: string[] = [];
  const checked = (name: string) => { checks.push(name); console.log(`PASS ${name}`); };
  async function call<T>(method: string, body: unknown, expectedCode?: string,
    options?: { rawBody?: string; contentType?: string }): Promise<T> {
    const response = await rpc.response(`api/${method}`, body, options);
    const text = response.body.toString();
    calls.push({ method, request: options?.rawBody ?? body, status: response.status,
      rpcStatus: response.headers['gkd-rpc-status'], response: text });
    assert.equal(response.status, 200);
    assert.equal(response.headers['gkd-rpc-status'], expectedCode ? 'error' : 'ok');
    assert.match(response.headers['content-type']!, /^application\/json/);
    const data = JSON.parse(text);
    if (expectedCode) {
      assert.equal(data.code, expectedCode);
      assert.equal(data.stackTrace, null);
      assert.ok(data.message.length > 0);
    }
    return data as T;
  }
  const read = (subsId = -2) => call<Rules>('getRules', { subsId });
  const appId = `li.gkd.http.check.run${Date.now()}`;
  const appTarget = (groupKey: number) => ({ groupType: 2, appId, groupKey });
  const globalTarget = (groupKey: number, targetApp: string | null = null) => ({ groupType: 3, appId: targetApp, groupKey });
  const formalFile = join(session, 'data', 'files', 'subscription', '-2.json');
  let failure: string | undefined;
  try {
    await desktop.serviceCommand('Http', true);
    assert.equal((await desktop.services()).runtime.Http.phase, 'Running');
    const info = await call<{ device: { device: string } }>('getServerInfo', {});
    assert.equal(info.device.device, 'Desktop');
    checked('Desktop lifecycle starts the real HTTP listener');
    const original = await read();
    await call('getRules', { subsId: 13 }, 'INVALID_REQUEST');
    await call('getRules', {}, 'INVALID_REQUEST', { rawBody: 'broken-json' });
    await call('getRules', { subsId: -2 }, 'INVALID_REQUEST', { contentType: 'text/plain' });
    await call('setSubscriptionEnabled', { subsId: -2, enabled: {} }, 'INVALID_REQUEST');
    checked('Request decoding, content type and target validation return INVALID_REQUEST');

    const added = await call<Group[]>('appendRules', { subsId: -2, groupType: 2, appId,
      source: `{id:'${appId}',groups:[{key:0,name:'HTTP first',rules:[{matches:'[text="close"]'}]}]}` });
    const key = added[0]!.key;
    const collision = await call<Group[]>('appendRules', { subsId: -2, groupType: 2, appId,
      source: `{key:${key},name:'HTTP second',rules:[]}` });
    assert.notEqual(collision[0]!.key, key);
    checked('App rule append persists content and allocates noncolliding keys');
    const committed = await readFile(formalFile, 'utf8');
    const invalid = await call<RpcError>('replaceRuleGroup', { subsId: -2, target: appTarget(key),
      source: "{name:'bad',rules:[{actionMaximum:'bad'}]}" }, 'INVALID_SUBSCRIPTION');
    assert.equal(invalid.path, '$.rules[0].actionMaximum');
    assert.match(invalid.message, /Expected/);
    const syntax = await call<RpcError>('appendRules', { subsId: -2, groupType: 2, appId, source: '{' }, 'INVALID_SUBSCRIPTION');
    assert.equal(syntax.path, '$');
    await call('appendRules', { subsId: -2, groupType: 2, appId,
      source: "{name:'invalid selector',rules:[{matches:'['}]}" }, 'INVALID_SELECTOR');
    await call('appendRules', { subsId: -2, groupType: 2, appId,
      source: "{name:'HTTP first',rules:[]}" }, 'RuleNameDuplicate');
    assert.equal(await readFile(formalFile, 'utf8'), committed);
    checked('Wrapped parser/selector errors retain detail and path without changing committed content');
    const request = { subsId: -2, target: appTarget(key), enable: false };
    const setting = await call<{ changed: number }>('setRuleGroupSetting', request);
    assert.equal(setting.changed, 1);
    assert.equal((await call<{ unchanged: number }>('setRuleGroupSetting', request)).unchanged, 1);
    const replacement = { subsId: -2, target: appTarget(key), source: `{key:999,name:'HTTP updated',rules:[{matches:'[text="new"]'}]}` };
    assert.equal(await call('replaceRuleGroup', replacement), true);
    assert.equal(await call('replaceRuleGroup', replacement), false);
    const updated = await read();
    assert.equal(updated.subscription.apps.find(a => a.id === appId)!.groups[0]!.key, key);
    assert.equal(updated.configuration.appGroupConfigs.find(c => c.appId === appId)!.enable, false);
    assert.equal(JSON.parse(await readFile(formalFile, 'utf8')).apps.find((a: { id: string }) => a.id === appId).groups[0].name, 'HTTP updated');
    checked('Replacement preserves target key and override; no-op returns false; file is persisted');
    for (const enable of [false, true, null]) {
      await call('setSubscriptionAppSetting', { subsId: -2, appId, enable });
      const configs = (await read()).configuration.appConfigs.filter(c => c.appId === appId);
      if (enable === null) assert.equal(configs.length, 0); else assert.equal(configs[0]!.enable, enable);
    }
    checked('App setting supports true, false and null');
    for (const enabled of [true, false]) {
      await call('setSubscriptionEnabled', { subsId: -2, enabled });
      assert.equal((await read()).configuration.subsItems[0]!.enable, enabled);
    }
    await call('setSubscriptionEnabled', { subsId: -2, enabled: original.configuration.subsItems[0]!.enable });
    checked('Subscription switch persists both values');
    const global = await call<Group[]>('appendRules', { subsId: -2, groupType: 3, appId: null,
      source: `{key:0,name:'HTTP global',rules:[{matches:'[text="global"]'}]}` });
    const globalKey = global[0]!.key;
    await call('setRuleGroupSetting', { subsId: -2, target: globalTarget(globalKey), enable: false });
    await call('setRuleGroupSetting', { subsId: -2, target: globalTarget(globalKey, appId), enable: false });
    await call('setRuleGroupSetting', { subsId: -2, target: globalTarget(globalKey), enable: null });
    assert.equal((await read()).configuration.globalGroupConfigs.find(c => c.groupKey === globalKey)!.enable, null);
    await call('setRuleGroupSetting', { subsId: -2, target: globalTarget(globalKey, appId), enable: null });
    assert.equal((await read()).configuration.globalGroupConfigs.filter(c => c.groupKey === globalKey).length, 0);
    checked('Global group and per-app settings share the existing config and clear empty records');

    await call('updateSubscription', { id: 1, name: 'HTTP fixture', version: 1,
      categories: [{ key: 5, name: 'HTTP category', enable: false }], apps: [], globalGroups: [] });
    const memoryFile = join(session, 'data', 'files', 'subscription', '-1.json');
    const memory = await readFile(memoryFile, 'utf8');
    const direct = await call<RpcError>('updateSubscription', { id: 'bad', name: 'fixture', version: 1 }, 'INVALID_SUBSCRIPTION');
    assert.equal(direct.path, '$.id');
    assert.equal(await readFile(memoryFile, 'utf8'), memory);
    checked('Direct subscription parser exposes INVALID_SUBSCRIPTION and leaves the previous file intact');
    for (const config of [null, { enable: null }, { enable: false }, { enable: true }, null]) {
      await call('setCategorySetting', { subsId: -1, categoryKey: 5, config });
      const configs = (await read(-1)).configuration.categoryConfigs;
      if (config === null) assert.equal(configs.length, 0); else assert.equal(configs[0]!.enable, config.enable);
    }
    await call('setCategorySetting', { subsId: -1, categoryKey: 999, config: null }, 'CategoryMissing');
    await call('setCategorySetting', { subsId: -1, categoryKey: 5 }, 'INVALID_REQUEST');
    checked('Category four states distinguish missing records from enable:null');
    await call('captureSnapshot', {}, 'PLATFORM_UNSUPPORTED');
    await call('execSelector', { selector: '[text="test"]' }, 'PLATFORM_UNSUPPORTED');
    await call('getSnapshot', { id: -999 }, 'SNAPSHOT_NOT_FOUND');
    checked('Inspection exceptions also pass through the shared business-error interface');
    assert.equal(await call('deleteRuleGroup', { subsId: -2, target: globalTarget(globalKey) }), 1);
    assert.equal(await call('deleteRuleGroup', { subsId: -2, target: globalTarget(globalKey) }), 0);
    for (const groupKey of [key, collision[0]!.key]) {
      assert.equal(await call('deleteRuleGroup', { subsId: -2, target: appTarget(groupKey) }), 1);
    }
    const deleted = await read();
    assert.ok(!deleted.subscription.apps.some(a => a.id === appId));
    assert.ok(!deleted.configuration.appGroupConfigs.some(c => c.appId === appId));
    await call('replaceRuleGroup', replacement, 'RuleMissing');
    checked('Delete returns 1/0, cleans empty apps/configs and exposes RuleMissing');
    for (const subsId of [-2, -1]) {
      const { configuration } = await read(subsId);
      assert.ok(configuration.subsItems.every(c => c.id === subsId));
      for (const configs of [configuration.appConfigs, configuration.appGroupConfigs, configuration.globalGroupConfigs, configuration.categoryConfigs]) {
        assert.ok(configs.every(c => c.subsId === subsId));
      }
    }
    checked('Read responses contain configuration only for the requested subscription');
  } catch (error) {
    failure = error instanceof Error ? error.stack : String(error);
    throw error;
  } finally {
    await mkdir(dirname(reportFile), { recursive: true });
    await writeFile(reportFile, JSON.stringify({ session, rpcPort, passed: !failure, checks, calls, failure }, null, 2));
    console.log(`Report: ${reportFile}`);
  }
  return { reportFile, checks: checks.length, calls: calls.length };
}
