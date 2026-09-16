import { apiFetch, streamSse, type SseHandlers } from './client'

export interface MessageDto {
  role: string
  content: string
  createdAt: string
}

export interface StartSessionResponse {
  conversationId: number
  messages: MessageDto[]
}

export interface SendMessageResponse {
  reply: string
  profileUpdated: boolean
}

export type PricingModel = 'HOURLY' | 'PER_UNIT' | 'PER_PROJECT' | 'INDIVIDUAL'

export interface PricingServiceEntry {
  name: string
  pricingModel: PricingModel | null
  unit: string | null
  basePrice: number | null
  minPrice: number | null
  maxPrice: number | null
  minimumCharge: number | null
  factors: string[] | null
  requiresIndividualQuote: boolean | null
  notes: string | null
}

export interface ProfileResponse {
  services: PricingServiceEntry[]
  generalNotes: string | null
  updatedAt: string | null
  manuallyEditedAt: string | null
}

export interface UpdateProfileRequest {
  services: PricingServiceEntry[]
  generalNotes: string | null
}

export function startSession(): Promise<StartSessionResponse> {
  return apiFetch('/api/onboarding/sessions', { method: 'POST' })
}

export function streamOnboardingMessage(
  conversationId: number,
  content: string,
  handlers: SseHandlers<SendMessageResponse>,
): Promise<void> {
  return streamSse(
    `/api/onboarding/sessions/${conversationId}/messages`,
    { method: 'POST', body: JSON.stringify({ content }) },
    handlers,
  )
}

export function getProfile(): Promise<ProfileResponse> {
  return apiFetch('/api/onboarding/profile')
}

export function updateProfile(request: UpdateProfileRequest): Promise<ProfileResponse> {
  return apiFetch('/api/onboarding/profile', {
    method: 'PATCH',
    body: JSON.stringify(request),
  })
}

export interface CompanyResponse {
  id: number
  name: string
  slug: string
  status: string
}

export function completeOnboarding(): Promise<CompanyResponse> {
  return apiFetch('/api/onboarding/complete', { method: 'POST' })
}
