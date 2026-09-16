import { apiFetch, fetchAuthorizedBlobUrl } from './client'

export interface QuoteLineItem {
  name: string
  description: string | null
  quantity: number | null
  unit: string | null
  unitPrice: number | null
  totalPrice: number | null
  source: string | null
}

export interface QuoteChangeLogEntry {
  createdAt: string
  summary: string
}

export interface QuoteResponse {
  id: number
  companyId: number
  conversationId: number
  leadId: number
  status: string
  currency: string
  items: QuoteLineItem[]
  subtotal: number
  total: number
  aiConfidence: number | null
  aiReasoning: string | null
  uncertainFactors: string[]
  aiItems: QuoteLineItem[]
  aiSubtotal: number
  aiTotal: number
  clientNote: string | null
  internalNote: string | null
  estimatedTimeline: string | null
  offerValidUntil: string | null
  approvedAt: string | null
  changeLog: QuoteChangeLogEntry[]
  createdAt: string
  updatedAt: string
  clientName: string | null
  clientPhone: string | null
  clientEmail: string | null
}

export interface UpdateQuoteRequest {
  items: QuoteLineItem[]
  clientNote: string | null
  internalNote: string | null
  estimatedTimeline: string | null
  offerValidUntil: string | null
}

export function listQuotes(): Promise<QuoteResponse[]> {
  return apiFetch('/api/quotes')
}

export function getQuote(id: number): Promise<QuoteResponse> {
  return apiFetch(`/api/quotes/${id}`)
}

export function getQuoteByLead(leadId: number): Promise<QuoteResponse> {
  return apiFetch(`/api/quotes/by-lead/${leadId}`)
}

export function updateQuote(id: number, request: UpdateQuoteRequest): Promise<QuoteResponse> {
  return apiFetch(`/api/quotes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function approveQuote(id: number): Promise<QuoteResponse> {
  return apiFetch(`/api/quotes/${id}/approve`, { method: 'POST' })
}

export function getQuotePdfUrl(id: number): Promise<string> {
  return fetchAuthorizedBlobUrl(`/api/quotes/${id}/pdf`)
}
