import { setTimeout as pause } from 'node:timers/promises';

export type MatchDebugRun = {
  phase: 'running' | 'cancelling' | 'completed' | 'cancelled' | 'failed';
  error?: string;
  result?: { matched: boolean; reason: string; action?: { result: boolean; shell: boolean } };
};

export type DebugWait = <T>(read: () => Promise<T>, ready: (value: T) => boolean,
  timeout?: number, cancellable?: boolean) => Promise<T>;

export function createDebugWait(signal: AbortSignal, defaultTimeout: number, interval: number, label: string): DebugWait {
  return async <T>(read: () => Promise<T>, ready: (value: T) => boolean,
    timeout = defaultTimeout, cancellable = true): Promise<T> => {
    const deadline = Date.now() + timeout;
    let latest: T;
    do {
      if (cancellable) signal.throwIfAborted();
      latest = await read();
      if (ready(latest)) return latest;
      await pause(interval);
    } while (Date.now() < deadline);
    throw new Error(`${label} timed out: ${JSON.stringify(latest)}`);
  };
}

export function waitForDebugRun(read: () => Promise<MatchDebugRun>, wait: DebugWait, timeout: number) {
  return wait(read, run => run.phase === 'completed' || run.phase === 'failed' || run.phase === 'cancelled', timeout);
}
