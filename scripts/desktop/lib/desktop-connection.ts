import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { localRoot } from './local-paths.ts';

/** Each host publishes its actual bound port; isolated hosts require their own connection file. */
export async function resolveDesktopPort(explicitPort?: string, connection?: string): Promise<number> {
  if (explicitPort && connection) throw new Error('Use either --port or --connection');
  const port: unknown = explicitPort === undefined
    ? JSON.parse(await readFile(connection ?? join(localRoot, 'desktop', 'debug-server.json'), 'utf8')).port
    : Number(explicitPort);
  if (typeof port !== 'number' || !Number.isInteger(port) || port < 1000 || port > 65535) {
    throw new Error('Invalid Desktop debug service port');
  }
  return port;
}
