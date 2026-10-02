import { request } from 'node:http';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

export { delay };
export type WindowId = 'app' | 'controls';
export interface WindowSnapshot {
  customFrame: boolean; x: number; y: number; width: number; height: number;
  contentX: number; contentY: number; contentWidth: number; contentHeight: number;
  minimized: boolean; maximized: boolean;
}
export interface UiNode {
  id: string; role: string; name: string | null; text?: string | null; editable: boolean;
  actions: string[]; states: string[]; children: UiNode[];
  bounds?: { x: number; y: number; width: number; height: number } | null;
}
export interface Environment {
  width: number; height: number; density: number; fontScale: number;
  dark: boolean; locale: string;
  android: Record<string, number | boolean | string>;
}
export type SimulatorSettings = Record<string, Record<string, unknown>>;
export interface DesktopState {
  simulator: SimulatorSettings;
  isolated: boolean; page: string; variant: string; environment: Environment; lastEvent: string | null;
}
export function nodes(root: UiNode): UiNode[] {
  return [root, ...root.children.flatMap(nodes)];
}
export async function until<T>(read: () => Promise<T>, accept: (value: T) => boolean, label: string): Promise<T> {
  const deadline = Date.now() + 6_000;
  do {
    const value = await read();
    if (accept(value)) return value;
    await delay(100);
  } while (Date.now() < deadline);
  throw new Error(`Timed out: ${label}`);
}
export class DesktopClient {
  readonly port: number;
  constructor(port: number) { this.port = port; }
  // Explicit direct localhost transport, independent of environment proxy settings.
  bytes(path: string, body?: unknown): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      const req = request({ hostname: '127.0.0.1', port: this.port, path: `/${path}`,
        method: body === undefined ? 'GET' : 'POST',
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
      }, res => {
        const chunks: Buffer[] = [];
        res.on('data', chunk => chunks.push(Buffer.from(chunk)));
        res.on('error', reject);
        res.on('end', () => {
          const data = Buffer.concat(chunks);
          if (res.statusCode !== 200) reject(new Error(`HTTP ${res.statusCode}: ${data.toString()}`));
          else resolve(data);
        });
      });
      req.setTimeout(20_000, () => req.destroy(new Error(`Request timeout: ${path}`)));
      req.on('error', reject);
      req.end(body === undefined ? undefined : JSON.stringify(body));
    });
  }
  async json<T>(path: string, body?: unknown): Promise<T> {
    return JSON.parse((await this.bytes(path, body)).toString()) as T;
  }
  state() { return this.json<DesktopState>('state'); }
  window(window: WindowId = 'app') { return this.json<WindowSnapshot>(`window?window=${window}`); }
  scenario(page: string, extra: Record<string, unknown> = {}) { return this.json('scenario', { page, ...extra }); }
  replaceSimulator(value: SimulatorSettings) { return this.json('simulator', value); }
  patchSimulator(value: SimulatorSettings) { return this.json('simulator/patch', value); }
  action(value: Record<string, unknown>) { return this.json('action', value); }
  key(key = 'Escape') { return this.action({ type: 'key', key }); }
  async tree(window: WindowId = 'app') { return nodes(await this.json<UiNode>(`semantics?window=${window}`)); }
  async find(match: (node: UiNode) => boolean, window: WindowId = 'app') {
    const found = await until(async () => (await this.tree(window)).filter(match), n => n.length === 1, `one matching UI node: ${match}`);
    return found[0]!;
  }
  async operate(match: (node: UiNode) => boolean, action: Record<string, unknown>, window: WindowId = 'app') {
    for (let attempt = 0; attempt < 6; attempt++) {
      const node = await this.find(match, window);
      try { await this.action({ ...action, node: node.id, window }); return; }
      catch (error) {
        if (!(error instanceof Error) || !error.message.includes('Stale node path')) throw error;
        await delay(50);
      }
    }
    throw new Error('UI node kept changing');
  }
  click(name: string | RegExp, window: WindowId = 'app') {
    console.log(`Click ${window}: ${name}`);
    return this.operate(n => n.actions.includes('click') &&
      (typeof name === 'string' ? n.name === name : name.test(n.name ?? '')), { type: 'invoke' }, window);
  }
  tab(name: string) { return this.operate(n => n.role === 'page tab' && n.name === name, { type: 'invoke' }); }
  text(text: string) { return this.operate(n => n.editable, { type: 'text', text }); }
  page(page: string) { return until(() => this.state(), s => s.page === page, `page ${page}`); }
  async screenshot(path: string, window: WindowId = 'app', area: 'content' | 'frame' = 'content', activate = false) {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, await this.bytes(`screenshot?window=${window}&area=${area}&activate=${activate}`));
  }
}
