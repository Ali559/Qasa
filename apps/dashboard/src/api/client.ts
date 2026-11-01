import { ref } from 'vue'

const API_BASE = import.meta.env.VITE_QASA_API_URL as string | undefined

const TOKEN_KEY = 'qasa.admin.token'
const ROLE_KEY = 'qasa.admin.role'
const EMAIL_KEY = 'qasa.admin.email'

export type AdminRole = 'admin' | 'viewer'

export function isLiveApiEnabled(): boolean {
  return Boolean(API_BASE?.trim())
}

function readRole(): AdminRole {
  try {
    return localStorage.getItem(ROLE_KEY) === 'viewer' ? 'viewer' : 'admin'
  } catch {
    return 'admin'
  }
}

/** Signed-in admin's role and email, shared across the app. Demo mode is always `admin`. */
export const adminRole = ref<AdminRole>(readRole())
export const adminEmail = ref<string | null>(localStorage.getItem(EMAIL_KEY))

/** False for `viewer` admins, whose every write is refused with 403 by the API. */
export function canWrite(): boolean {
  return !isLiveApiEnabled() || adminRole.value === 'admin'
}

export function getAuthToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function setSession(session: { token: string; role: string; email: string } | null) {
  setAuthToken(session?.token ?? null)
  if (session) {
    const role: AdminRole = session.role === 'viewer' ? 'viewer' : 'admin'
    localStorage.setItem(ROLE_KEY, role)
    localStorage.setItem(EMAIL_KEY, session.email)
    adminRole.value = role
    adminEmail.value = session.email
  } else {
    localStorage.removeItem(ROLE_KEY)
    localStorage.removeItem(EMAIL_KEY)
    adminRole.value = 'admin'
    adminEmail.value = null
  }
}

/** Error carrying the HTTP status and the API's `{error, message}` body. */
export class ApiError extends Error {
  status: number
  code?: string
  constructor(status: number, message: string, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/** Human-readable message for a toast description. */
export function errorMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (err instanceof ApiError) {
    if (err.status === 403 && err.code === 'forbidden') return 'Your role is read-only.'
    if (err.status === 429) return 'Too many attempts. Wait a few minutes and try again.'
    return err.message || fallback
  }
  return err instanceof Error ? err.message : fallback
}

async function toApiError(res: Response): Promise<ApiError> {
  const text = await res.text()
  try {
    const body = JSON.parse(text) as { error?: string; message?: string }
    const message =
      body.message ??
      (body.error === 'forbidden'
        ? 'Your role is read-only.'
        : body.error === 'invalid_credentials'
          ? 'Wrong email or password.'
          : body.error === 'not_found'
            ? 'Not found.'
            : body.error)
    return new ApiError(res.status, message || `HTTP ${res.status}`, body.error)
  } catch {
    return new ApiError(res.status, text || `HTTP ${res.status}`)
  }
}

/** Like `apiFetch`, but also returns the response headers (e.g. `X-Next-Before`). */
export async function apiFetchWithHeaders<T>(
  path: string,
  init: RequestInit = {},
): Promise<{ data: T; headers: Headers }> {
  if (!API_BASE) {
    throw new Error('VITE_QASA_API_URL is not configured')
  }

  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const token = getAuthToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${API_BASE.replace(/\/$/, '')}${path}`, {
    ...init,
    headers,
  })

  const isLogin = path.startsWith('/v1/admin/auth/login')
  if (res.status === 401 && !isLogin) {
    // Expired, logged out elsewhere, or the admin was disabled: back to sign-in.
    const err = await toApiError(res)
    setSession(null)
    if (!window.location.pathname.startsWith('/login')) {
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.assign(`/login?redirect=${redirect}`)
    }
    throw err
  }

  if (!res.ok) {
    throw await toApiError(res)
  }

  if (res.status === 204) {
    return { data: undefined as T, headers: res.headers }
  }
  const text = await res.text()
  return { data: (text ? JSON.parse(text) : undefined) as T, headers: res.headers }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  return (await apiFetchWithHeaders<T>(path, init)).data
}

/** Build a query string, skipping empty values. */
export function qs(params: Record<string, string | number | null | undefined>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}
