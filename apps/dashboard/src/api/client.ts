const API_BASE = import.meta.env.VITE_QASA_API_URL as string | undefined

export function isLiveApiEnabled(): boolean {
  return Boolean(API_BASE?.trim())
}

export function getAuthToken(): string | null {
  return localStorage.getItem('qasa.admin.token')
}

export function setAuthToken(token: string | null) {
  if (token) localStorage.setItem('qasa.admin.token', token)
  else localStorage.removeItem('qasa.admin.token')
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
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

  if (res.status === 401) {
    setAuthToken(null)
    throw new Error('Unauthorized')
  }

  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }

  return res.json() as Promise<T>
}
