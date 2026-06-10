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

  constructor(message: string, status: number, path: string) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.path = path;
  }
}

const TOKEN_KEY = "grun.admin.accessToken";
const REFRESH_TOKEN_KEY = "grun.admin.refreshToken";
const UNAUTHORIZED_EVENT = "grun-admin-unauthorized";
const DEFAULT_TIMEOUT_MS = 20000;

export function getToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function saveTokens(response: LoginResponse) {
  window.localStorage.setItem(TOKEN_KEY, response.token);
  if (response.refreshToken) {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
  }
}

export function clearTokens() {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function isUnauthorizedError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 401;
}

export function formatRequestError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) return "Session expired. Please sign in again.";
    if (error.status === 403) return "This account does not have admin permission for this action.";
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Request failed";
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: { email, password }
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
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    const message = extractErrorMessage(data) ?? `Request failed with status ${response.status}`;
    throw new ApiRequestError(message, response.status, path);
  }
  return data as T;
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
