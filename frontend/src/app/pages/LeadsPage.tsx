import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listLeadsOverview, type LeadOverview, type LeadOverviewFilterValue } from '../../shared/api/leadApi'
import { LEAD_STATUS_LABELS, LEAD_STATUS_TONE } from '../../shared/api/leadStatus'
import { QUOTE_STATUS_LABELS, QUOTE_STATUS_TONE } from '../../shared/api/quoteStatus'
import { Alert, Badge, Card, EmptyState } from '../../shared/ui'
import './LeadsPage.css'

const FILTERS: { value: LeadOverviewFilterValue; label: string }[] = [
  { value: 'ALL', label: 'Wszystkie' },
  { value: 'NEW', label: 'Nowe' },
  { value: 'AWAITING_QUOTE', label: 'Oczekujące na wycenę' },
  { value: 'AWAITING_APPROVAL', label: 'Oczekujące na akceptację' },
  { value: 'SENT', label: 'Wysłane' },
  { value: 'WON', label: 'Wygrane' },
  { value: 'LOST', label: 'Przegrane' },
]

function formatDate(value: string) {
  return new Date(value).toLocaleString('pl-PL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatQuoteTotal(lead: LeadOverview) {
  if (lead.quoteTotal == null) return null
  return `${lead.quoteTotal} PLN`
}

function actionFor(lead: LeadOverview): { label: string; to: string } | null {
  if (lead.quoteId == null) return null
  const to = `/app/quotes/${lead.quoteId}`
  switch (lead.quoteStatus) {
    case 'WAITING_FOR_OWNER':
      return { label: 'Akceptuj', to }
    case 'OWNER_EDITED':
      return { label: 'Edytuj wycenę', to }
    case 'APPROVED':
      return { label: 'Wyślij ofertę', to }
    case 'SEND_FAILED':
      return { label: 'Wyślij ofertę', to }
    default:
      return { label: 'Przejdź do wyceny', to }
  }
}

function LeadsPage() {
  const [leads, setLeads] = useState<LeadOverview[]>([])
  const [filter, setFilter] = useState<LeadOverviewFilterValue>('ALL')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listLeadsOverview(filter)
      .then(setLeads)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać leadów'))
      .finally(() => setLoading(false))
  }, [filter])

  return (
    <main className="leads-page">
      <div>
        <h1>Leady</h1>
        <p className="leads-subtitle">Zapytania od klientów zebrane przez Twojego asystenta AI.</p>
      </div>

      <div className="leads-filters">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={`leads-filter-chip ${filter === f.value ? 'leads-filter-chip-active' : ''}`}
            onClick={() => setFilter(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {!loading && leads.length === 0 && !error && (
        <Card>
          <EmptyState title="Brak leadów" description="Gdy klient zakończy rozmowę z AI i zostawi dane kontaktowe, pojawi się tutaj." />
        </Card>
      )}

      {leads.length > 0 && (
        <Card padded={false}>
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Klient</th>
                  <th>Opis</th>
                  <th>Data</th>
                  <th>Status leada</th>
                  <th>Status wyceny</th>
                  <th>Kwota</th>
                  <th>Akcja</th>
                </tr>
              </thead>
              <tbody>
                {leads.map((lead) => {
                  const total = formatQuoteTotal(lead)
                  const action = actionFor(lead)
                  return (
                    <tr key={lead.leadId}>
                      <td data-label="Klient">
                        <Link className="leads-client-link" to={`/app/leads/${lead.leadId}`}>
                          {lead.clientName}
                        </Link>
                      </td>
                      <td data-label="Opis">
                        <span className="leads-description">{lead.description ?? '—'}</span>
                      </td>
                      <td data-label="Data">{formatDate(lead.createdAt)}</td>
                      <td data-label="Status leada">
                        <Badge tone={LEAD_STATUS_TONE[lead.leadStatus] ?? 'neutral'}>
                          {LEAD_STATUS_LABELS[lead.leadStatus] ?? lead.leadStatus}
                        </Badge>
                      </td>
                      <td data-label="Status wyceny">
                        {lead.quoteStatus ? (
                          <Badge tone={QUOTE_STATUS_TONE[lead.quoteStatus] ?? 'neutral'}>
                            {QUOTE_STATUS_LABELS[lead.quoteStatus] ?? lead.quoteStatus}
                          </Badge>
                        ) : (
                          <span className="leads-price-empty">Brak wyceny</span>
                        )}
                        {lead.awaitingApproval && <span className="leads-awaiting-badge">czeka na akceptację</span>}
                      </td>
                      <td data-label="Kwota">
                        <span className={total ? 'leads-price' : 'leads-price-empty'}>{total ?? '—'}</span>
                      </td>
                      <td data-label="Akcja">
                        {action ? (
                          <Link className="leads-action-link" to={action.to}>
                            {action.label}
                          </Link>
                        ) : (
                          <span className="leads-price-empty">—</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </main>
  )
}

export default LeadsPage
