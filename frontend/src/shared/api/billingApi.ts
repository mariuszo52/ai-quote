import { apiFetch } from './client'

export type CompanyPlanValue = 'TRIAL' | 'STARTER' | 'GROWTH' | 'PRO'

export interface BillingStatus {
  plan: CompanyPlanValue
  planDisplayName: string
  subscriptionStatus: 'NONE' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED'
  trialEndsAt: string | null
  currentPeriodEnd: string | null
  quotesUsed: number
  quoteLimit: number
  canManageBilling: boolean
  blocked: boolean
}

interface RedirectUrlResponse {
  url: string
}

export function getBillingStatus(): Promise<BillingStatus> {
  return apiFetch('/api/billing/status')
}

export async function startCheckout(plan: Exclude<CompanyPlanValue, 'TRIAL'>): Promise<string> {
  const response = await apiFetch<RedirectUrlResponse>('/api/billing/checkout', {
    method: 'POST',
    body: JSON.stringify({ plan }),
  })
  return response.url
}

export async function openBillingPortal(): Promise<string> {
  const response = await apiFetch<RedirectUrlResponse>('/api/billing/portal', { method: 'POST' })
  return response.url
}
