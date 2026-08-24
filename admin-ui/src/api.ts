export type LoginResponse = {
  token: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  message?: string;
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
const AUTH_CHANNEL = "grun-admin-auth";
const UNAUTHORIZED_EVENT = "grun-admin-unauthorized";
const ACTIVITY_EVENT = "grun-admin-activity";
const REAUTH_EVENT = "grun-admin-reauth-required";
const REAUTH_RESULT_EVENT = "grun-admin-reauth-result";
let reauthCodeResolver: ((code: string | null) => void) | null = null;
const DEFAULT_TIMEOUT_MS = 20000;

export function getToken(): string | null { return accessToken; }

export function saveTokens(response: LoginResponse) { accessToken = response.token; }

export function clearTokens(broadcast = true) {
  accessToken = null;
  if (broadcast && "BroadcastChannel" in window) {
    const channel = new BroadcastChannel(AUTH_CHANNEL);
    channel.postMessage({ type: "logout" });
    channel.close();
  }
}

export async function restoreAdminSession(): Promise<boolean> {
  try { saveTokens(await request<LoginResponse>("/api/v1/auth/admin/refresh", { method: "POST", auth: false })); return true; }
  catch { clearTokens(false); return false; }
}

export async function logoutAdmin(): Promise<void> {
  try { await request("/api/v1/auth/admin/logout", { method: "POST", auth: false }); } finally { clearTokens(); }
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
  } = {}
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
      throw new ApiRequestError("Request timed out. Check whether the backend is running.", 0, path);
    }
    throw new ApiRequestError(error instanceof Error ? error.message : "Network request failed", 0, path);
  } finally {
    window.clearTimeout(timeoutId);
  }

  const text = await response.text();
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
  return data as T;
}


export async function requestFormData<T>(
  path: string,
  formData: FormData,
  options: { method?: string; timeoutMs?: number } = {}
): Promise<T> {
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
  window.dispatchEvent(new CustomEvent(ACTIVITY_EVENT));
  return data as T;
}

export async function requestBlob(
  path: string,
  options: { timeoutMs?: number; headers?: Record<string, string>; reauthRetry?: boolean } = {},
): Promise<Blob> {
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
  window.dispatchEvent(new CustomEvent(ACTIVITY_EVENT));
  return response.blob();
}
export function subscribeAdminActivity(handler: () => void): () => void {
  window.addEventListener(ACTIVITY_EVENT, handler);
  return () => window.removeEventListener(ACTIVITY_EVENT, handler);
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
