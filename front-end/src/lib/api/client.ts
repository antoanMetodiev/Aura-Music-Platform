import type { ApiErrorBody } from "@/types/api";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

/** Thrown for both real API errors (with a code/traceId) and network-level failures. */
export class ApiError extends Error {
  readonly code: string;
  readonly traceId: string | null;
  readonly status: number | null;

  constructor(message: string, opts: { code: string; traceId?: string | null; status?: number | null; cause?: unknown }) {
    super(message, { cause: opts.cause });
    this.name = "ApiError";
    this.code = opts.code;
    this.traceId = opts.traceId ?? null;
    this.status = opts.status ?? null;
  }
}

interface ApiFetchOptions {
  searchParams?: Record<string, string | number | string[] | undefined>;
  /** Next.js fetch cache behavior. Catalog data is provider-backed and changes over time, so the
   * default is "no-store" — callers can opt into ISR-style revalidation once we actually want it. */
  next?: RequestInit["next"];
  signal?: AbortSignal;
  /**
   * Bearer token for user-scoped endpoints. Client code passes `await getClientAccessToken()`
   * (`@/lib/api/token`), server components `await getAccessToken()` (`@/lib/auth/session`).
   * Public catalog/playback calls leave it out.
   */
  token?: string | null;
}

/**
 * The single place the frontend talks to the backend (FRONTEND.md §7). Goes through the API
 * Gateway (`NEXT_PUBLIC_API_BASE_URL`, default `http://localhost:8080/api/v1`), so every service
 * behind it is reachable the same way. Sends `Authorization: Bearer <JWT>` when the caller hands
 * over a token (see `token` above); the services verify it against Better Auth's JWKS.
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`);
  for (const [key, value] of Object.entries(options.searchParams ?? {})) {
    if (value === undefined) continue;
    if (Array.isArray(value)) {
      for (const v of value) url.searchParams.append(key, v);
    } else {
      url.searchParams.set(key, String(value));
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      cache: "no-store",
      next: options.next,
      signal: options.signal,
      headers: options.token
        ? { Accept: "application/json", Authorization: `Bearer ${options.token}` }
        : { Accept: "application/json" },
    });
  } catch (cause) {
    // Backend unreachable (not running, wrong URL, network blip) — distinguish from a real API error
    // so callers can show "search is unavailable" instead of crashing the page.
    throw new ApiError("Could not reach the backend API", { code: "NETWORK_ERROR", status: null, cause });
  }

  if (!response.ok) {
    const body = await safeJson<ApiErrorBody>(response);
    throw new ApiError(body?.message ?? `Request failed with status ${response.status}`, {
      code: body?.code ?? "UNKNOWN_ERROR",
      traceId: body?.traceId,
      status: response.status,
    });
  }

  return (await response.json()) as T;
}

async function safeJson<T>(response: Response): Promise<T | null> {
  try {
    return (await response.json()) as T;
  } catch {
    return null;
  }
}
