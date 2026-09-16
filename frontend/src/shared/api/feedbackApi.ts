import { apiFetch } from './client'
import type { QuoteLineItem } from './quotesApi'

export type FeedbackReason =
  | 'MISSING_ITEM'
  | 'PRICE_TOO_LOW'
  | 'PRICE_TOO_HIGH'
  | 'EXTRA_DIFFICULTY'
  | 'UNUSUAL_CONDITIONS'
  | 'MATERIALS'
  | 'LABOR'
  | 'OTHER'

export const FEEDBACK_REASON_LABELS: Record<FeedbackReason, string> = {
  MISSING_ITEM: 'Brakujący element',
  PRICE_TOO_LOW: 'Zbyt niska cena',
  PRICE_TOO_HIGH: 'Zbyt wysoka cena',
  EXTRA_DIFFICULTY: 'Dodatkowa trudność',
  UNUSUAL_CONDITIONS: 'Nietypowe warunki',
  MATERIALS: 'Materiały',
  LABOR: 'Robocizna',
  OTHER: 'Inny',
}

export interface ChangedItem {
  name: string
  aiQuantity: number | null
  aiUnitPrice: number | null
  aiTotalPrice: number | null
  finalQuantity: number | null
  finalUnitPrice: number | null
  finalTotalPrice: number | null
}

export interface QuoteFeedbackResponse {
  id: number
  quoteId: number
  aiTotal: number
  finalTotal: number
  diffAmount: number
  diffPercentage: number | null
  aiItems: QuoteLineItem[]
  finalItems: QuoteLineItem[]
  changedItems: ChangedItem[]
  addedItems: QuoteLineItem[]
  removedItems: QuoteLineItem[]
  reason: FeedbackReason | null
  note: string | null
  reasonSubmittedAt: string | null
  createdAt: string
}

export function getQuoteFeedback(quoteId: number): Promise<QuoteFeedbackResponse> {
  return apiFetch(`/api/quotes/${quoteId}/feedback`)
}

export function submitQuoteFeedback(quoteId: number, reason: FeedbackReason | null, note: string | null): Promise<QuoteFeedbackResponse> {
  return apiFetch(`/api/quotes/${quoteId}/feedback`, {
    method: 'PUT',
    body: JSON.stringify({ reason, note }),
  })
}
