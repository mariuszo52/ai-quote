import { API_BASE_URL, apiFetch, streamSse, type SseHandlers } from './client'

export interface CompanyPublicInfo {
  name: string
  ready: boolean
  displayName: string
  primaryColor: string
  welcomeText: string
  hasLogo: boolean
}

export function companyLogoUrl(slug: string): string {
  return `${API_BASE_URL}/api/public/companies/${slug}/logo`
}

export interface StartConversationResponse {
  conversationId: number
  token: string
  companyName: string
  greeting: string
  options: string[]
}

export interface QuoteDto {
  minPrice: number
  maxPrice: number
  currency: string
  reasoning: string
  uncertainFactors: string[]
}

export interface QuoteMessageResponse {
  reply: string
  quote: QuoteDto | null
  options: string[]
}

export interface ContactPayload {
  name: string
  phone: string
  email?: string
}

export function getCompanyPublicInfo(slug: string): Promise<CompanyPublicInfo> {
  return apiFetch(`/api/public/companies/${slug}`)
}

export function startConversation(slug: string): Promise<StartConversationResponse> {
  return apiFetch(`/api/public/companies/${slug}/conversations`, { method: 'POST' })
}

export function streamQuoteMessage(
  conversationId: number,
  token: string,
  content: string,
  handlers: SseHandlers<QuoteMessageResponse>,
): Promise<void> {
  return streamSse(
    `/api/public/conversations/${conversationId}/messages`,
    {
      method: 'POST',
      headers: { 'X-Conversation-Token': token },
      body: JSON.stringify({ content }),
    },
    handlers,
  )
}

export function submitContact(
  conversationId: number,
  token: string,
  contact: ContactPayload,
): Promise<{ leadId: number }> {
  return apiFetch(`/api/public/conversations/${conversationId}/submit-contact`, {
    method: 'POST',
    headers: { 'X-Conversation-Token': token },
    body: JSON.stringify(contact),
  })
}

export interface AttachmentUploadedResponse {
  attachmentId: number
  originalFilename: string
}

export function uploadAttachment(conversationId: number, token: string, file: File): Promise<AttachmentUploadedResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch(`/api/public/conversations/${conversationId}/attachments`, {
    method: 'POST',
    headers: { 'X-Conversation-Token': token },
    body: formData,
  })
}
