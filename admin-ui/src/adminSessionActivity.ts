import type { AdminSessionTiming } from "./api";

/** Human input and server deadlines are separate from background API traffic. */
type Options = {
  now: () => number;
  readSession: () => AdminSessionTiming | null;
  renew: (humanActivity: boolean) => Promise<boolean>;
  onWarning: (visible: boolean, remainingMs: number, absolute: boolean) => void;
  onExpired: () => void;
  onFailure: (error: unknown) => void;
  onContinued?: (at: number) => void;
};

export function createAdminSessionActivity(options: Options) {
  let lastActivity = options.now();
  let acknowledgedActivity = lastActivity;
  let lastAttempt = -Infinity;
  let warning = false;
  let stopped = false;
  let pending: Promise<boolean> | null = null;

  function remaining() {
    const state = options.readSession();
    return state ? Math.min(state.idleExpiresAt, state.absoluteExpiresAt) - options.now() : 0;
  }

  function expire() {
    if (stopped) return;
    stopped = true;
    options.onWarning(false, 0, false);
    options.onExpired();
  }

  function sync(humanActivity: boolean): Promise<boolean> {
    if (stopped) return Promise.resolve(false);
    if (pending) return pending;
    const activityAtStart = lastActivity;
    lastAttempt = options.now();
    pending = (async () => {
      try {
        const valid = await options.renew(humanActivity);
        if (stopped) return false;
        if (!valid || !options.readSession() || remaining() <= 0) { expire(); return false; }
        if (humanActivity) acknowledgedActivity = activityAtStart;
        return true;
      } catch (error) {
        if (!stopped) {
          options.onFailure(error);
          // Offline never grants time beyond the last server-confirmed deadline.
          if (remaining() <= 0) expire();
        }
        return false;
      } finally {
        pending = null;
      }
    })();
    return pending;
  }

  function activity(at = options.now(), remote = false) {
    if (stopped || !Number.isFinite(at) || at > options.now() || at <= lastActivity) return false;
    const state = options.readSession();
    if (!state || options.now() >= state.absoluteExpiresAt) { expire(); return false; }
    if (remaining() <= 0) {
      // Another tab may have renewed the server session. Verify, never revive it by touch.
      void sync(false);
      return false;
    }
    if (warning && !remote) return false;
    lastActivity = at;
    if (remote) warning = false;
    return true;
  }

  function tick() {
    if (stopped) return;
    const state = options.readSession();
    if (!state || options.now() >= state.absoluteExpiresAt) { expire(); return; }
    if (remaining() <= 0) { void sync(false); return; }
    const heartbeatMs = Math.min(30_000, state.idleTimeoutMs / 4);
    const freshInput = lastActivity > acknowledgedActivity && options.now() - lastActivity < heartbeatMs;
    if (freshInput && options.now() - lastAttempt >= heartbeatMs) void sync(true);
    else if (state.tokenExpiresAt - options.now() <= 15_000 && options.now() - lastAttempt >= 1000) {
      // Token rotation alone must not extend idle time.
      void sync(false);
    }
    const absolute = state.absoluteExpiresAt <= state.idleExpiresAt;
    warning = remaining() <= Math.min(120_000, state.idleTimeoutMs / 4)
      && (absolute || !freshInput);
    options.onWarning(warning, remaining(), absolute);
  }

  async function continueSession() {
    if (stopped) return;
    const state = options.readSession();
    if (!state || options.now() >= state.absoluteExpiresAt) { expire(); return; }
    if (remaining() <= 0 && !await sync(false)) return;
    if (pending) await pending;
    if (stopped) return;
    lastActivity = options.now();
    if (await sync(true)) {
      warning = false;
      options.onWarning(false, remaining(), false);
      options.onContinued?.(lastActivity);
    }
  }

  return { activity, tick, continueSession, stop: () => { stopped = true; } };
}
