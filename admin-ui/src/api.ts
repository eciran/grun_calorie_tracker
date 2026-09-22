export type LoginResponse = {
  token: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  message?: string;
  adminSession?: {
    sessionId: string;
    serverTime: string;
    idleExpiresAt: string;
    absoluteExpiresAt: string;
    idleTimeoutMs: number;
    tokenExpiresAt: string;
  };
};

export type AdminSessionTiming = {
  sessionId: string;
  idleExpiresAt: number;
  absoluteExpiresAt: number;
  idleTimeoutMs: number;
  tokenExpiresAt: number;
};

export type PageResponse<T> = {
  content?: T[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};

export class ApiRequestError extends Error {
  readonly status: number;
  readonly path: string;
  readonly correlationId?: string;

  constructor(message: string, status: number, path: string, correlationId?: string) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.path = path;
    this.correlationId = correlationId;
  }
}

let accessToken: string | null = null;
let tokenGeneration = 0;
let sessionTiming: AdminSessionTiming | null = null;
let sessionRequest: Promise<boolean> | null = null;
const AUTH_CHANNEL = "grun-admin-auth";
const UNAUTHORIZED_EVENT = "grun-admin-unauthorized";
const REAUTH_EVENT = "grun-admin-reauth-required";
const REAUTH_RESULT_EVENT = "grun-admin-reauth-result";
let reauthCodeResolver: ((code: string | null) => void) | null = null;
const DEFAULT_TIMEOUT_MS = 20000;
let telemetryFlushInFlight = false;

type CachedAdminResponse = { expiresAt: number; value: unknown };
const adminResponseCache = new Map<string, CachedAdminResponse>();
const adminRequestsInFlight = new Map<string, Promise<unknown>>();
const SHORT_CACHE_TTL_MS = 15_000;
const REPORT_CACHE_TTL_MS = 30_000;
const REFERENCE_CACHE_TTL_MS = 5 * 60_000;

function adminCacheTtl(path: string): number {
  const pathname = path.split("?", 1)[0];
  if (!pathname.startsWith("/api/v1/admin/")) return 0;
  if (/\/(security|mail)(\/|$)/.test(pathname) || /evidence-url$/.test(pathname)) return 0;
  if (/\/(catalog|notification-definitions|subscriptions\/plans)(\/|$)/.test(pathname)) return REFERENCE_CACHE_TTL_MS;
  if (/\/(reports|analytics|metrics|summary)(\/|$|-)/.test(pathname)) return REPORT_CACHE_TTL_MS;
  return SHORT_CACHE_TTL_MS;
}

export function clearAdminRequestCache(pathPrefix?: string) {
  for (const key of adminResponseCache.keys()) {
    const path = key.slice(key.indexOf(":") + 1);
    if (!pathPrefix || path.startsWith(pathPrefix)) adminResponseCache.delete(key);
  }
}

type ClientFailure = { source: "ADMIN_WEB"; failureKind: "NETWORK" | "TIMEOUT"; method: string; route: string; occurredAt: string; durationMs: number; clientPlatform: "BROWSER"; appVersion: "admin-ui-v2" };
const clientFailureQueue: ClientFailure[] = [];
function safeTelemetryRoute(path: string): string {
  try {
    const pathname = new URL(path, window.location.origin).pathname;
    if (!pathname.startsWith("/api/") || pathname.startsWith("/api/v1/error-telemetry/")) return "/api/[client-unmapped]";
    return pathname.split("/").map(segment => /^(\d+|[0-9a-f]{8}-[0-9a-f-]{27,})$/i.test(segment) || segment.length > 48 || /%40|@/i.test(segment) ? "{id}" : segment.replace(/[^A-Za-z0-9_.{}\-\[\]]/g,"_")).join("/").slice(0,300);
  } catch { return "/api/[client-unmapped]"; }
}
function queueClientFailure(kind: "NETWORK"|"TIMEOUT", path: string, method: string, durationMs: number) {
  if (path.startsWith("/api/v1/error-telemetry/")) return;
  try {
    clientFailureQueue.push({ source:"ADMIN_WEB",failureKind:kind,method,route:safeTelemetryRoute(path),occurredAt:new Date().toISOString(),durationMs:Math.min(300000,Math.max(0,durationMs)),clientPlatform:"BROWSER",appVersion:"admin-ui-v2" });
    if(clientFailureQueue.length>20)clientFailureQueue.splice(0,clientFailureQueue.length-20);
  } catch { /* Telemetry must never break the admin request. */ }
}
async function flushClientFailures() {
  if (telemetryFlushInFlight || !accessToken) return; telemetryFlushInFlight=true;
  try {
    const item=clientFailureQueue[0]; if(!item)return;
    const response=await fetch("/api/v1/error-telemetry/client",{method:"POST",headers:{Accept:"application/json","Content-Type":"application/json",Authorization:`Bearer ${accessToken}`},body:JSON.stringify(item)});
    if(response.ok)clientFailureQueue.shift();
  } catch { /* Keep the bounded item for the next successful request. */ } finally { telemetryFlushInFlight=false; }
}

export function getToken(): string | null { return accessToken; }

export function saveTokens(response: LoginResponse) {
  accessToken = response.token;
  const state = response.adminSession;
  sessionTiming = null;
  if (state) {
    const offset = Date.now() - Date.parse(state.serverTime);
    const timing = {
      sessionId: state.sessionId,
      idleExpiresAt: Date.parse(state.idleExpiresAt) + offset,
      absoluteExpiresAt: Date.parse(state.absoluteExpiresAt) + offset,
      tokenExpiresAt: Date.parse(state.tokenExpiresAt) + offset,
      idleTimeoutMs: state.idleTimeoutMs,
    };
    if (timing.sessionId && timing.idleTimeoutMs > 0 &&
      [timing.idleExpiresAt, timing.absoluteExpiresAt, timing.tokenExpiresAt, timing.idleTimeoutMs].every(Number.isFinite)) {
      sessionTiming = timing;
    }
  }
}

export function getAdminSessionTiming(): AdminSessionTiming | null { return sessionTiming; }

export function clearTokens(broadcast = true) {
  tokenGeneration += 1;
  accessToken = null;
  sessionTiming = null;
  sessionRequest = null;
  clearAdminRequestCache();
  if (broadcast && "BroadcastChannel" in window) {
    const channel = new BroadcastChannel(AUTH_CHANNEL);
    channel.postMessage({ type: "logout" });
    channel.close();
  }
}

export async function restoreAdminSession(): Promise<boolean> {
  return updateAdminSession(false);
}

export async function renewAdminSession(): Promise<boolean> {
  // Wait for a read-only restore, then explicitly acknowledge human activity.
  if (sessionRequest && !await sessionRequest) return false;
  return updateAdminSession(true);
}

function updateAdminSession(humanActivity: boolean): Promise<boolean> {
  if (sessionRequest) return sessionRequest;
  const pending = performSessionUpdate(humanActivity);
  sessionRequest = pending;
  void pending.finally(() => { if (sessionRequest === pending) sessionRequest = null; }).catch(() => undefined);
  return pending;
}

async function performSessionUpdate(humanActivity: boolean): Promise<boolean> {
  const generation = tokenGeneration;
  try {
    const result = await request<LoginResponse>(humanActivity ? "/api/v1/auth/admin/refresh" : "/api/v1/auth/admin/session", {
      method: humanActivity ? "POST" : "GET", auth: false
    });
    if (generation !== tokenGeneration) return false;
    saveTokens(result);
    return true;
  } catch (error) {
    if (generation !== tokenGeneration) return false;
    if (error instanceof ApiRequestError && [400, 401, 403].includes(error.status)) {
      clearTokens(false);
      return false;
    }
    throw error;
  }
}

export async function logoutAdmin(): Promise<void> {
  clearTokens();
  await request("/api/v1/auth/admin/logout", { method: "POST", auth: false });
}

/** Used only to scope cross-tab messages, never to authorize an operation. */
export function adminSessionId(): string | null {
  if (sessionTiming) return sessionTiming.sessionId;
  try {
    const payload = accessToken?.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const value = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="))).adminSessionId;
    return typeof value === "string" ? value : null;
  } catch { return null; }
}

function requestReauthCode(purpose: string): Promise<string | null> {
  if (reauthCodeResolver) reauthCodeResolver(null);
  window.dispatchEvent(new CustomEvent(REAUTH_EVENT, { detail: { purpose } }));
  return new Promise((resolve) => { reauthCodeResolver = resolve; });
}
export function resolveAdminReauth(code: string | null) { const resolver = reauthCodeResolver; reauthCodeResolver = null; resolver?.(code); }
export function subscribeAdminReauth(handler: (purpose: string) => void): () => void { const listener = (event: Event) => handler(String((event as CustomEvent).detail?.purpose ?? "")); window.addEventListener(REAUTH_EVENT, listener); return () => window.removeEventListener(REAUTH_EVENT, listener); }
export function subscribeAdminReauthResult(handler: (result: { success: boolean; message?: string }) => void): () => void { const listener = (event: Event) => handler((event as CustomEvent).detail ?? { success: false }); window.addEventListener(REAUTH_RESULT_EVENT, listener); return () => window.removeEventListener(REAUTH_RESULT_EVENT, listener); }

export function isUnauthorizedError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 401;
}

export function formatRequestError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) return "Session expired. Please sign in again.";
    if (error.status === 403) return error.message || "This account does not have admin permission for this action.";
    return error.correlationId ? `${error.message} (Reference: ${error.correlationId})` : error.message;
  }
  if (error instanceof Error) return error.message;
  return "Request failed";
}

export async function login(email: string, password: string, adminMfaCode?: string): Promise<LoginResponse> {
  return request<LoginResponse>("/api/v1/auth/admin/login", {
    method: "POST",
    auth: false,
    body: { email, password, adminMfaCode: adminMfaCode?.trim() || undefined }
  });
}

export async function request<T>(
  path: string,
  options: {
    method?: string;
    auth?: boolean;
    body?: unknown;
    headers?: Record<string, string>;
    timeoutMs?: number;
    reauthRetry?: boolean;
    cacheTtlMs?: number;
    bypassCache?: boolean;
  } = {}
): Promise<T> {
  if (options.auth !== false) await ensureAdminAccess();
  const method = options.method ?? "GET";
  const ttl = options.bypassCache ? 0 : (options.cacheTtlMs ?? (method === "GET" && options.auth !== false ? adminCacheTtl(path) : 0));
  const cacheKey = `${tokenGeneration}:${path}`;
  if (ttl > 0) {
    const cached = adminResponseCache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) return Promise.resolve(cached.value as T);
    if (cached) adminResponseCache.delete(cacheKey);
    const pending = adminRequestsInFlight.get(cacheKey);
    if (pending) return pending as Promise<T>;
  }
  const pending = executeRequest<T>(path, options).then((value) => {
    if (ttl > 0) adminResponseCache.set(cacheKey, { expiresAt: Date.now() + ttl, value });
    return value;
  }).finally(() => adminRequestsInFlight.delete(cacheKey));
  if (ttl > 0) adminRequestsInFlight.set(cacheKey, pending);
  return pending;
}

async function executeRequest<T>(
  path: string,
  options: {
    method?: string;
    auth?: boolean;
    body?: unknown;
    headers?: Record<string, string>;
    timeoutMs?: number;
    reauthRetry?: boolean;
    cacheTtlMs?: number;
    bypassCache?: boolean;
  }
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...options.headers
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (options.auth !== false) {
    const token = getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  const startedAt = Date.now(); const requestMethod = options.method ?? "GET";
  let response: Response;
  try {
    response = await fetch(path, {
      method: options.method ?? "GET",
      cache: (options.method ?? "GET") === "GET" ? "no-store" : undefined,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      if(options.auth!==false)queueClientFailure("TIMEOUT",path,requestMethod,Date.now()-startedAt);
      throw new ApiRequestError("Request timed out. Check whether the backend is running.", 0, path);
    }
    if(options.auth!==false)queueClientFailure("NETWORK",path,requestMethod,Date.now()-startedAt);
    throw new ApiRequestError(error instanceof Error ? error.message : "Network request failed", 0, path);
  } finally {
    window.clearTimeout(timeoutId);
  }

  const text = await response.text();
  if(options.auth!==false)void flushClientFailures();
  const data = text ? safeJson(text) : null;
  if (!response.ok) {
    if (response.status === 428 && options.reauthRetry !== false && data && typeof data === "object") {
      const purpose = String((data as Record<string, unknown>).requiredPurpose ?? "");
      const code = await requestReauthCode(purpose);
      if (code && purpose) {
        try {
          const proof = await request<{ token?: string }>("/api/v1/admin/security/mfa/reauthenticate", { method: "POST", body: { code, purpose }, reauthRetry: false });
          if (proof.token) {
            window.dispatchEvent(new CustomEvent(REAUTH_RESULT_EVENT, { detail: { success: true } }));
            return request<T>(path, { ...options, headers: { ...options.headers, "X-Admin-Reauth-Token": proof.token }, reauthRetry: false });
          }
        } catch (error) {
          const message = error instanceof ApiRequestError && error.status === 400
            ? `Authenticator or recovery code is invalid.${error.correlationId ? ` (Reference: ${error.correlationId})` : ""}`
            : formatRequestError(error);
          window.dispatchEvent(new CustomEvent(REAUTH_RESULT_EVENT, { detail: { success: false, message } }));
          return request<T>(path, options);
        }
      }
    }
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    const message = extractErrorMessage(data) ?? `Request failed with status ${response.status}`;
    const correlationId = data && typeof data === "object" ? String((data as Record<string, unknown>).correlationId ?? "") || undefined : undefined;
    throw new ApiRequestError(message, response.status, path, correlationId);
  }
  if ((options.method ?? "GET") !== "GET") clearAdminRequestCache();
  return data as T;
}


export async function requestFormData<T>(
  path: string,
  formData: FormData,
  options: { method?: string; timeoutMs?: number } = {}
): Promise<T> {
  await ensureAdminAccess();
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(path, {
      method: options.method ?? "POST",
      headers,
      body: formData,
      signal: controller.signal,
      credentials: "include"
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiRequestError("Request timed out. Check whether the backend is running.", 0, path);
    }
    throw new ApiRequestError(error instanceof Error ? error.message : "Network request failed", 0, path);
  } finally {
    window.clearTimeout(timeoutId);
  }
  const text = await response.text();
  const data = text ? safeJson(text) : null;
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    const message = extractErrorMessage(data) ?? `Request failed with status ${response.status}`;
    const correlationId = data && typeof data === "object" ? String((data as Record<string, unknown>).correlationId ?? "") || undefined : undefined;
    throw new ApiRequestError(message, response.status, path, correlationId);
  }
  return data as T;
}

export async function requestBlob(
  path: string,
  options: { timeoutMs?: number; headers?: Record<string, string>; reauthRetry?: boolean } = {},
): Promise<Blob> {
  await ensureAdminAccess();
  const headers: Record<string, string> = { Accept: "text/csv", ...options.headers };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(path, { headers, signal: controller.signal, credentials: "include" });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiRequestError("Request timed out. Check whether the backend is running.", 0, path);
    }
    throw new ApiRequestError(error instanceof Error ? error.message : "Network request failed", 0, path);
  } finally {
    window.clearTimeout(timeoutId);
  }
  if (!response.ok) {
    const text = await response.text();
    const data = text ? safeJson(text) : null;
    if (response.status === 401) window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    if (response.status === 428 && options.reauthRetry !== false && data && typeof data === "object") {
      const purpose = (data as { requiredPurpose?: string }).requiredPurpose;
      if (purpose) {
        const code = await requestReauthCode(purpose);
        if (code) {
          const proof = await request<{ token?: string }>("/api/v1/admin/security/mfa/reauthenticate", {
            method: "POST",
            body: { code, purpose },
            reauthRetry: false,
          });
          if (proof.token) {
            return requestBlob(path, {
              ...options,
              headers: { ...options.headers, "X-Admin-Reauth-Token": proof.token },
              reauthRetry: false,
            });
          }
        }
      }
    }
    const message = extractErrorMessage(data) ?? `Request failed with status ${response.status}`;
    const correlationId = data && typeof data === "object" ? String((data as Record<string, unknown>).correlationId ?? "") || undefined : undefined;
    throw new ApiRequestError(message, response.status, path, correlationId);
  }
  return response.blob();
}

async function ensureAdminAccess(): Promise<void> {
  if (!accessToken || !sessionTiming || sessionTiming.tokenExpiresAt - Date.now() > 5000) return;
  // Refresh the bearer before a resumed tab sends its first request; do not retry a mutation.
  if (!await restoreAdminSession()) {
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    throw new ApiRequestError("Session expired. Please sign in again.", 401, "/api/v1/auth/admin/session");
  }
}

export function subscribeUnauthorized(handler: () => void): () => void {
  window.addEventListener(UNAUTHORIZED_EVENT, handler);
  return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler);
}

function extractErrorMessage(data: unknown): string | null {
  if (!data || typeof data !== "object") return null;
  const payload = data as Record<string, unknown>;
  for (const key of ["message", "error", "detail", "title"]) {
    if (typeof payload[key] === "string" && payload[key]) return String(payload[key]);
  }
  return null;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
