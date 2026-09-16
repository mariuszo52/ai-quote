import { apiFetch } from './client'

export type DashboardRangeValue = 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS'

export interface CurrencyAmount {
  currency: string
  amount: number
}

export interface DashboardResponse {
  range: string
  inquiries: number
  newLeads: number
  quotesGenerated: number
  pendingApproval: number
  offersSent: number
  won: number
  lost: number
  inquiryToLeadPercent: number | null
  leadToSentQuotePercent: number | null
  sentQuoteToWonPercent: number | null
  sentOffersTotal: CurrencyAmount[]
  wonOffersTotal: CurrencyAmount[]
  averageWonOfferValue: CurrencyAmount[]
  averageAiDiffPercentage: number | null
  percentQuotesChangedByOwner: number | null
}

export function getDashboard(range: DashboardRangeValue = 'TODAY'): Promise<DashboardResponse> {
  return apiFetch(`/api/dashboard?range=${range}`)
}
