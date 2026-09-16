import { API_BASE_URL, apiFetch, fetchAuthorizedBlobUrl } from './client'

export interface OfferLineItem {
  name: string
  description: string | null
  quantity: number | null
  unit: string | null
  unitPrice: number | null
  totalPrice: number | null
}

export interface OfferResponse {
  id: number
  quoteId: number
  publicToken: string
  status: string
  currency: string
  items: OfferLineItem[]
  total: number
  clientName: string
  clientPhone: string
  clientEmail: string | null
  jobDescription: string | null
  estimatedTimeline: string | null
  validUntil: string | null
  sentAt: string | null
  lastSendError: string | null
  createdAt: string
}

export interface PublicOfferResponse {
  offerNumber: number
  companyName: string
  companyContactEmail: string | null
  currency: string
  items: OfferLineItem[]
  total: number
  clientName: string
  jobDescription: string | null
  estimatedTimeline: string | null
  validUntil: string | null
  createdAt: string
}

export function getOffer(quoteId: number): Promise<OfferResponse> {
  return apiFetch(`/api/quotes/${quoteId}/offer`)
}

export function getOfferPdfUrl(quoteId: number): Promise<string> {
  return fetchAuthorizedBlobUrl(`/api/quotes/${quoteId}/offer/pdf`)
}

export function sendOffer(quoteId: number): Promise<OfferResponse> {
  return apiFetch(`/api/quotes/${quoteId}/offer/send`, { method: 'POST' })
}

export function getPublicOffer(token: string): Promise<PublicOfferResponse> {
  return apiFetch(`/api/public/offers/${token}`)
}

export function publicOfferPdfUrl(token: string): string {
  return `${API_BASE_URL}/api/public/offers/${token}/pdf`
}
