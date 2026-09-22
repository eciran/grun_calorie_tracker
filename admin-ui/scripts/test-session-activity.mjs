import assert from "node:assert/strict";
import fs from "node:fs";
import ts from "typescript";

async function load(relative) {
  const source = fs.readFileSync(new URL(relative, import.meta.url), "utf8");
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`);
}

const { createAdminSessionActivity } = await load("../src/adminSessionActivity.ts");
const minute = 60_000;
function fixture(renew = async () => true) {
  let now = 0, calls = 0, expired = 0, failures = 0;
  const warnings = [];
  const modes = [];
  const state = { sessionId: "sid", idleExpiresAt: 15 * minute, absoluteExpiresAt: 8 * 60 * minute,
    idleTimeoutMs: 15 * minute, tokenExpiresAt: 60 * minute };
  const activity = createAdminSessionActivity({
    now: () => now,
    readSession: () => state,
    renew: async (human) => {
      calls++; modes.push(human);
      if (now >= state.idleExpiresAt || now >= state.absoluteExpiresAt) return false;
      const ok = await renew();
      if (ok) {
        if (human) state.idleExpiresAt = now + state.idleTimeoutMs;
        state.tokenExpiresAt = Math.min(now + 60 * minute, state.absoluteExpiresAt);
      }
      return ok;
    },
    onWarning: value => { if (value !== (warnings.at(-1) ?? false)) warnings.push(value); },
    onExpired: () => expired++,
    onFailure: () => failures++,
  });
  return { activity, warnings, state, modes, at: value => { now = value; },
    get calls() { return calls; }, get expired() { return expired; }, get failures() { return failures; } };
}
const settle = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };

// Continuous form editing needs no business API request and never warns.
const active = fixture();
for (let now = 10_000; now <= 60 * minute; now += 10_000) {
  active.at(now); active.activity.activity(); active.activity.tick(); await settle();
}
assert.deepEqual(active.warnings, []);
assert.equal(active.expired, 0);
assert.ok(active.calls > 0 && active.calls <= 120, "heartbeats must be throttled");

// Background timer ticks alone never renew, and wake-up checks use elapsed wall time.
const idle = fixture();
idle.at(13 * minute); idle.activity.tick();
assert.deepEqual(idle.warnings, [true]);
assert.equal(idle.calls, 0);
idle.at(40 * minute); idle.activity.tick(); idle.activity.tick();
await settle();
assert.equal(idle.expired, 1);
assert.deepEqual(idle.modes, [false], "expired local deadlines must be verified without extending them");

// Repeated Continue actions rearm the full idle/warning cycle, including the second warning.
const repeat = fixture();
for (let cycle = 0; cycle < 3; cycle++) {
  repeat.at((cycle * 14 + 13) * minute); repeat.activity.tick();
  assert.equal(repeat.warnings.at(-1), true);
  repeat.at((cycle * 14 + 14) * minute); await repeat.activity.continueSession();
  assert.equal(repeat.warnings.at(-1), false);
}
assert.equal(repeat.calls, 3);
assert.equal(repeat.expired, 0);

const remote = fixture();
remote.at(13 * minute); remote.activity.tick();
assert.equal(remote.activity.activity(), false, "incidental input must not confirm an open warning");
assert.equal(remote.activity.activity(13 * minute, true), true);
remote.activity.tick(); await settle();
assert.equal(remote.warnings.at(-1), false);
assert.equal(remote.activity.activity(20 * minute, true), false, "future broadcasts must be rejected");
remote.at(30 * minute);
assert.equal(remote.activity.activity(30 * minute, true), false, "expired clients must not be revived");
await settle();
assert.equal(remote.expired, 1);

// Configured server timeout and restored remaining lifetime override UI defaults.
const custom = fixture();
custom.state.idleTimeoutMs = 4 * minute;
custom.state.idleExpiresAt = 90_000;
custom.at(35_000); custom.activity.tick();
assert.equal(custom.warnings.at(-1), true);
custom.at(60_000); await custom.activity.continueSession();
assert.equal(custom.state.idleExpiresAt, 5 * minute);

const rotation = fixture();
rotation.state.tokenExpiresAt = minute;
rotation.at(50_000); rotation.activity.tick(); await settle();
assert.deepEqual(rotation.modes, [false]);
assert.equal(rotation.state.idleExpiresAt, 15 * minute, "token rotation cannot prolong inactivity");

const absolute = fixture();
absolute.state.absoluteExpiresAt = minute;
absolute.at(minute); await absolute.activity.continueSession();
assert.equal(absolute.expired, 1);
assert.equal(absolute.calls, 0);

// A sleeping tab verifies peer-renewed deadlines without registering fake activity.
let staleState = { sessionId: "peer", idleExpiresAt: 0, absoluteExpiresAt: 999_999,
  idleTimeoutMs: 60_000, tokenExpiresAt: 0 };
let peerExpired = false;
const peerModes = [];
const peer = createAdminSessionActivity({ now: () => 5000, readSession: () => staleState,
  renew: async human => { peerModes.push(human); staleState = { ...staleState, idleExpiresAt: 65_000, tokenExpiresAt: 65_000 }; return true; },
  onWarning: () => {}, onExpired: () => { peerExpired = true; }, onFailure: error => { throw error; } });
peer.tick(); await settle();
assert.deepEqual(peerModes, [false]); assert.equal(peerExpired, false);

const network = fixture(async () => { throw new Error("offline"); });
network.at(minute); network.activity.activity(); network.activity.tick(); await settle();
assert.equal(network.failures, 1); assert.equal(network.expired, 0);
const revoked = fixture(async () => false);
revoked.at(minute); revoked.activity.activity(); revoked.activity.tick(); await settle();
assert.equal(revoked.expired, 1);

let finish;
const pending = fixture(() => new Promise(resolve => { finish = resolve; }));
pending.at(minute); pending.activity.activity(); pending.activity.tick(); pending.activity.tick();
assert.equal(pending.calls, 1);
pending.activity.stop(); finish(false); await settle();
assert.equal(pending.expired, 0, "disposed monitors must not update UI");

// Exercise the real API refresh code with transport and terminal failures.
globalThis.window = Object.assign(new EventTarget(), { setTimeout, clearTimeout });
const api = await load("../src/api.ts");
const paths = [];
const serverNow = Date.parse("2026-01-01T00:00:00Z");
globalThis.fetch = async (path, options) => {
  paths.push([path, options.method]);
  return new Response(JSON.stringify({ token: "jwt", adminSession: {
    sessionId: "sid", serverTime: new Date(serverNow).toISOString(),
    idleExpiresAt: new Date(serverNow + 90_000).toISOString(),
    absoluteExpiresAt: new Date(serverNow + 3600_000).toISOString(),
    tokenExpiresAt: new Date(serverNow + 60_000).toISOString(), idleTimeoutMs: 300_000,
  } }), { status: 200 });
};
assert.equal(await api.restoreAdminSession(), true);
assert.equal(await api.renewAdminSession(), true);
assert.deepEqual(paths, [["/api/v1/auth/admin/session", "GET"], ["/api/v1/auth/admin/refresh", "POST"]]);
assert.ok(Math.abs(api.getAdminSessionTiming().idleExpiresAt - Date.now() - 90_000) < 1000,
  "server/client clock skew must not shift the remaining session lifetime");
assert.equal(api.getAdminSessionTiming().idleTimeoutMs, 300_000);

// The first business request after sleep renews only its bearer, once, before sending.
api.getAdminSessionTiming().tokenExpiresAt = Date.now() - 1000;
paths.length = 0;
await api.request("/api/v1/admin/users", { method: "POST", body: { test: true } });
assert.deepEqual(paths, [["/api/v1/auth/admin/session", "GET"], ["/api/v1/admin/users", "POST"]]);

// Concurrent restore calls (including React StrictMode) share one request.
paths.length = 0;
await Promise.all([api.restoreAdminSession(), api.restoreAdminSession()]);
assert.equal(paths.length, 1);
api.saveTokens({ token: "existing" });
globalThis.fetch = async () => { throw new Error("offline"); };
await assert.rejects(api.restoreAdminSession());
assert.equal(api.getToken(), "existing", "network errors must not discard the active token");
globalThis.fetch = async () => new Response('{"message":"expired"}', { status: 400 });
assert.equal(await api.restoreAdminSession(), false);
assert.equal(api.getToken(), null);
let resolveResponse;
globalThis.fetch = () => new Promise(resolve => { resolveResponse = resolve; });
const staleRefresh = api.restoreAdminSession();
api.clearTokens(false);
resolveResponse(new Response('{"token":"late-token"}', { status: 200 }));
assert.equal(await staleRefresh, false);
assert.equal(api.getToken(), null, "a late refresh cannot undo logout");
console.log("Session activity behavior passed: active editing, idle, repeat warnings, cross-tab input, network failures and logout race.");
