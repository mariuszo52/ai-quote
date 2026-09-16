import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listQuotes, type QuoteResponse } from '../../shared/api/quotesApi'
import { QUOTE_STATUS_LABELS, QUOTE_STATUS_TONE } from '../../shared/api/quoteStatus'
import { Alert, Badge, Card, EmptyState } from '../../shared/ui'
import './QuotesPage.css'

function formatDate(value: string) {
  return new Date(value).toLocaleString('pl-PL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatConfidence(confidence: number | null) {
  if (confidence == null) return null
  return `${Math.round(confidence * 100)}%`
}

function QuotesPage() {
  const [quotes, setQuotes] = useState<QuoteResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listQuotes()
      .then(setQuotes)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać wycen'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <main className="quotes-page">
      <div>
        <h1>Wyceny oczekujące na akceptację</h1>
        <p className="quotes-subtitle">Robocze wyceny przygotowane przez AI po zakończonych rozmowach z klientami.</p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {!loading && quotes.length === 0 && !error && (
        <Card>
          <EmptyState
            title="Brak wycen"
            description="Gdy klient zakończy rozmowę i zostawi dane kontaktowe, AI przygotuje tu roboczą wycenę do sprawdzenia."
          />
        </Card>
      )}

      {quotes.length > 0 && (
        <Card padded={false}>
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Klient</th>
                  <th>Kwota</th>
                  <th>Pewność AI</th>
                  <th>Status</th>
                  <th>Data</th>
                </tr>
              </thead>
              <tbody>
                {quotes.map((quote) => {
                  const confidence = formatConfidence(quote.aiConfidence)
                  return (
                    <tr key={quote.id}>
                      <td data-label="Klient">
                        <Link className="quotes-client-link" to={`/app/quotes/${quote.id}`}>
                          {quote.clientName ?? `Wycena #${quote.id}`}
                        </Link>
                      </td>
                      <td data-label="Kwota">
                        <span className="quotes-total">
                          {quote.total} {quote.currency}
                        </span>
                      </td>
                      <td data-label="Pewność AI">
                        {confidence ?? <span className="quotes-confidence-empty">—</span>}
                      </td>
                      <td data-label="Status">
                        <Badge tone={QUOTE_STATUS_TONE[quote.status] ?? 'neutral'}>
                          {QUOTE_STATUS_LABELS[quote.status] ?? quote.status}
                        </Badge>
                      </td>
                      <td data-label="Data">{formatDate(quote.createdAt)}</td>
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

export default QuotesPage
