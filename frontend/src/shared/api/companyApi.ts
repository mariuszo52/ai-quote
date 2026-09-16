import { apiFetch, fetchAuthorizedBlobUrl } from './client'

export interface CompanyResponse {
  id: number
  name: string
  slug: string
  status: string
  displayName: string
  hasLogo: boolean
  primaryColor: string
  welcomeText: string
  contactEmail: string | null
  contactPhone: string | null
  address: string | null
}

export interface UpdateBrandingRequest {
  displayName: string | null
  primaryColor: string | null
  welcomeText: string | null
  contactEmail: string | null
  contactPhone: string | null
  address: string | null
}

export function getMyCompany(): Promise<CompanyResponse> {
  return apiFetch('/api/company/me')
}

export function updateBranding(request: UpdateBrandingRequest): Promise<CompanyResponse> {
  return apiFetch('/api/company/branding', {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function uploadLogo(file: File): Promise<CompanyResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch('/api/company/logo', { method: 'POST', body: formData })
}

export function removeLogo(): Promise<CompanyResponse> {
  return apiFetch('/api/company/logo', { method: 'DELETE' })
}

export function getMyLogoUrl(): Promise<string> {
  return fetchAuthorizedBlobUrl('/api/company/logo')
}
