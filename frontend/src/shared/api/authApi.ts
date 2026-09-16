import { apiFetch, setToken } from './client'

export interface AuthResponse {
  token: string
  companyId: number
  companySlug: string | null
}

export async function register(companyName: string, email: string, password: string): Promise<AuthResponse> {
  const response = await apiFetch<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ companyName, email, password }),
  })
  setToken(response.token)
  return response
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  setToken(response.token)
  return response
}

export async function googleLogin(idToken: string): Promise<AuthResponse> {
  const response = await apiFetch<AuthResponse>('/api/auth/google', {
    method: 'POST',
    body: JSON.stringify({ idToken }),
  })
  setToken(response.token)
  return response
}
