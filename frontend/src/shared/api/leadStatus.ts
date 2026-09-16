export const LEAD_STATUS_OPTIONS = ['NEW', 'CONTACTED', 'QUOTE_SENT', 'WON', 'LOST'] as const

export const LEAD_STATUS_LABELS: Record<string, string> = {
  NEW: 'Nowy',
  CONTACTED: 'Skontaktowano',
  QUOTE_SENT: 'Wycena wysłana',
  WON: 'Wygrany',
  LOST: 'Przegrany',
}

export const LEAD_STATUS_TONE: Record<string, 'info' | 'neutral' | 'warning' | 'success' | 'error'> = {
  NEW: 'info',
  CONTACTED: 'warning',
  QUOTE_SENT: 'success',
  WON: 'success',
  LOST: 'error',
}

/** Mirrors backend LeadStatusTransitions — QUOTE_SENT is deliberately absent as a
 * target: it's set automatically once the offer email actually sends, never manually. */
const MANUALLY_ALLOWED_TRANSITIONS: Record<string, readonly string[]> = {
  NEW: ['CONTACTED', 'LOST'],
  CONTACTED: ['WON', 'LOST'],
  QUOTE_SENT: ['WON', 'LOST'],
  WON: [],
  LOST: [],
}

export function allowedNextLeadStatuses(current: string): string[] {
  return [...(MANUALLY_ALLOWED_TRANSITIONS[current] ?? [])]
}
