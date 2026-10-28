/** GET /debug/readiness. A momentary observation, never an execution reservation. */
export interface DebugReadiness {
  app: { appId: string; enableAutomator: boolean; automatorMode: number; enableMatch: boolean };
  catalog: { loaded: boolean; refreshing: boolean; error: string | null; count: number };
  runtime: {
    engine: { attached: boolean; activeRunId: number | null; cancelling?: boolean };
    foreground: {
      interactive: boolean; locked: boolean; serviceMode: number | null; serviceEffective: boolean;
      foreground: { pending: unknown; confirmed: { appId: string; activityId: string | null } };
    } | null;
  };
  session: { id: string; active: boolean; systemFinalizing?: boolean } | null;
  checks: Record<'observe' | 'startCapture' | 'liveQuery' | 'androidBenchmark', { ready: boolean; reasons: string[] }>;
}
