export const QUOTE_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Szkic',
  WAITING_FOR_OWNER: 'Oczekuje na akceptację',
  OWNER_EDITED: 'Poprawione przez właściciela',
  APPROVED: 'Zatwierdzone',
  SENT_TO_CLIENT: 'Wysłane do klienta',
  SEND_FAILED: 'Błąd wysyłki',
  CANCELLED: 'Anulowane',
}

export const QUOTE_STATUS_TONE: Record<string, 'info' | 'neutral' | 'warning' | 'success' | 'error'> = {
  DRAFT: 'neutral',
  WAITING_FOR_OWNER: 'warning',
  OWNER_EDITED: 'info',
  APPROVED: 'success',
  SENT_TO_CLIENT: 'success',
  SEND_FAILED: 'error',
  CANCELLED: 'error',
}
