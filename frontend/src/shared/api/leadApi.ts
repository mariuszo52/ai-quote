import { apiFetch, fetchAuthorizedBlobUrl } from './client'
import type { MessageDto } from './onboardingApi'

export interface LeadSummary {
  id: number
  clientName: string
  clientPhone: string
  clientEmail: string | null
  estimatedPriceMin: number | null
  estimatedPriceMax: number | null
  currency: string | null
  status: string
  createdAt: string
}

export interface LeadAttachment {
  id: number
  filename: string
  contentType: string
}

export interface LeadDetail extends LeadSummary {
  aiSummary: string | null
  uncertainNotes: string | null
  transcript: MessageDto[]
  attachments: LeadAttachment[]
}

export interface LeadStatusUpdate {
  id: number
  status: string
}

/** One row of the leads panel — a Lead joined with its Quote, if one exists yet
 * (quoteId/quoteStatus/quoteTotal are null until the AI drafts one). Matches backend
 * quote.LeadOverviewResponse. */
export interface LeadOverview {
  leadId: number
  clientName: string
  clientPhone: string
  clientEmail: string | null
  description: string | null
  leadStatus: string
  createdAt: string
  quoteId: number | null
  quoteStatus: string | null
  quoteTotal: number | null
  awaitingApproval: boolean
}

export type LeadOverviewFilterValue = 'ALL' | 'NEW' | 'AWAITING_QUOTE' | 'AWAITING_APPROVAL' | 'SENT' | 'WON' | 'LOST'

export function getLeadAttachmentUrl(leadId: number, attachmentId: number): Promise<string> {
  return fetchAuthorizedBlobUrl(`/api/leads/${leadId}/attachments/${attachmentId}`)
}

export function listLeadsOverview(filter: LeadOverviewFilterValue = 'ALL'): Promise<LeadOverview[]> {
  return apiFetch(`/api/leads?filter=${filter}`)
}

export function getLead(id: number): Promise<LeadDetail> {
  return apiFetch(`/api/leads/${id}`)
}

export function updateLeadStatus(id: number, status: string): Promise<LeadStatusUpdate> {
  return apiFetch(`/api/leads/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}
