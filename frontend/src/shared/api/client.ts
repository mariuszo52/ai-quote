export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
const TOKEN_KEY = 'ai_quote_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}

const LOGIN_PATH = '/app/login'
const AUTH_ENDPOINT_PREFIX = '/api/auth/'

/**
 * A 401 or 403 on any authenticated request means the JWT is missing/expired/invalid
 * — JwtAuthenticationFilter just clears the SecurityContext on a bad token rather than
 * rejecting outright, and Spring Security's default entry point for "no authentication
 * present" is 403 (verified against the running backend), not 401. This app has no
 * role hierarchy beyond OWNER, so a 403 here never means "authenticated but lacking
 * permission" — it's always an invalid session. The app's own client-side auth check
 * (RequireAuth, LoginPage) only looks at whether a token is *present* in localStorage,
 * not whether it's still valid, so an expired session otherwise just shows broken/empty
 * pages instead of bouncing back to login. Login/register calls are excluded — their
 * 401 (wrong credentials) is a normal, inline-displayed form error, not an expired
 * session, and redirecting mid-submit would blow away that error before the user ever
 * sees it.
 */
function handleExpiredSession(path: string, status: number): void {
  if ((status !== 401 && status !== 403) || path.startsWith(AUTH_ENDPOINT_PREFIX)) {
    return
  }
  clearToken()
  if (window.location.pathname !== LOGIN_PATH) {
    window.location.href = LOGIN_PATH
  }
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const isFormData = options.body instanceof FormData
  const headers: HeadersInit = {
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })

  if (!response.ok) {
    handleExpiredSession(path, response.status)
    const body = await response.json().catch(() => null)
    throw new ApiError(response.status, body?.message ?? `Request failed: ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

/**
 * Fetches a binary resource (e.g. a lead attachment image) with the auth header and
 * returns a local object URL — plain <img src> can't attach an Authorization header itself.
 * Caller is responsible for URL.revokeObjectURL when done with it.
 */
export async function fetchAuthorizedBlobUrl(path: string): Promise<string> {
  const token = getToken()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    handleExpiredSession(path, response.status)
    throw new ApiError(response.status, `Request failed: ${response.status}`)
  }
  const blob = await response.blob()
  return URL.createObjectURL(blob)
}

export interface SseHandlers<T> {
  onDelta: (text: string) => void
  onDone: (payload: T) => void
  onError: (message: string) => void
}

/**
 * Native EventSource can't do POST bodies, custom headers, or auth — all of which our
 * streaming endpoints need (message content in the body, X-Conversation-Token for the
 * public quote chat). So this reads the text/event-stream response manually via fetch's
 * ReadableStream instead.
 */
export async function streamSse<T>(path: string, options: RequestInit, handlers: SseHandlers<T>): Promise<void> {
  const token = getToken()
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  } catch {
    handlers.onError('Nie udało się połączyć z serwerem.')
    return
  }

  if (!response.ok || !response.body) {
    handleExpiredSession(path, response.status)
    handlers.onError(`Request failed: ${response.status}`)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      handleSseEvent(rawEvent, handlers)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

function handleSseEvent<T>(rawEvent: string, handlers: SseHandlers<T>): void {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // Per the SSE spec, only a single leading space after "data:" is part of the
      // framing — a full trim() would also eat trailing spaces that are meaningful
      // content (e.g. the word-boundary spaces in streamed delta chunks).
      const value = line.slice(5)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  }

  const data = dataLines.join('\n')

  if (eventName === 'delta') {
    handlers.onDelta(data)
  } else if (eventName === 'done') {
    handlers.onDone(JSON.parse(data) as T)
  } else if (eventName === 'error') {
    handlers.onError(data)
  }
}
