import { useEffect, useState } from 'react'
import { getDashboard, type CurrencyAmount, type DashboardRangeValue, type DashboardResponse } from '../../shared/api/dashboardApi'
import { Alert, Card } from '../../shared/ui'
import './DashboardPage.css'

const RANGES: { value: DashboardRangeValue; label: string }[] = [
  { value: 'TODAY', label: 'Dzisiaj' },
  { value: 'LAST_7_DAYS', label: 'Ostatnie 7 dni' },
  { value: 'LAST_30_DAYS', label: 'Ostatnie 30 dni' },
]

function formatPercent(value: number | null): string {
  return value == null ? 'Brak wystarczających danych' : `${value.toFixed(1)}%`
}

function formatCurrencyAmounts(amounts: CurrencyAmount[]): string {
  if (amounts.length === 0) return 'Brak wystarczających danych'
  return amounts.map((a) => `${a.amount.toFixed(2)} ${a.currency}`).join(', ')
}

interface StatTileProps {
  label: string
  value: string | number
  tone?: 'neutral' | 'success' | 'error' | 'warning'
}

function StatTile({ label, value, tone = 'neutral' }: StatTileProps) {
  return (
    <div className={`dashboard-tile dashboard-tile-${tone}`}>
      <span className="dashboard-tile-label">{label}</span>
      <span className="dashboard-tile-value">{value}</span>
    </div>
  )
}

function DashboardPage() {
  const [range, setRange] = useState<DashboardRangeValue>('TODAY')
  const [data, setData] = useState<DashboardResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getDashboard(range)
      .then(setData)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać statystyk'))
  }, [range])

  return (
    <main className="dashboard-page">
      <div>
        <h1>Statystyki</h1>
        <p className="dashboard-subtitle">Skuteczność Twojego asystenta AI Quote.</p>
      </div>

      <div className="dashboard-filters">
        {RANGES.map((r) => (
          <button
            key={r.value}
            type="button"
            className={`leads-filter-chip ${range === r.value ? 'leads-filter-chip-active' : ''}`}
            onClick={() => setRange(r.value)}
          >
            {r.label}
          </button>
        ))}
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {data && (
        <>
          <Card>
            <h2>Ruch i statusy</h2>
            <div className="dashboard-tile-grid">
              <StatTile label="Wszystkie zapytania" value={data.inquiries} />
              <StatTile label="Nowe leady" value={data.newLeads} />
              <StatTile label="Wygenerowane wyceny" value={data.quotesGenerated} />
              <StatTile label="Oczekujące na akceptację" value={data.pendingApproval} tone="warning" />
              <StatTile label="Wysłane oferty" value={data.offersSent} />
              <StatTile label="Wygrane" value={data.won} tone="success" />
              <StatTile label="Przegrane" value={data.lost} tone="error" />
            </div>
          </Card>

          <Card>
            <h2>Konwersja</h2>
            <div className="dashboard-tile-grid">
              <StatTile label="Zapytanie → lead" value={formatPercent(data.inquiryToLeadPercent)} />
              <StatTile label="Lead → wysłana wycena" value={formatPercent(data.leadToSentQuotePercent)} />
              <StatTile label="Wysłana wycena → wygrana" value={formatPercent(data.sentQuoteToWonPercent)} />
            </div>
          </Card>

          <Card>
            <h2>Wartość</h2>
            <div className="dashboard-tile-grid">
              <StatTile label="Suma wysłanych ofert" value={formatCurrencyAmounts(data.sentOffersTotal)} />
              <StatTile label="Suma wygranych ofert" value={formatCurrencyAmounts(data.wonOffersTotal)} />
              <StatTile label="Średnia wygrana oferta" value={formatCurrencyAmounts(data.averageWonOfferValue)} />
            </div>
          </Card>

          <Card>
            <h2>AI</h2>
            <div className="dashboard-tile-grid">
              <StatTile
                label="Średnia różnica AI → finalna cena"
                value={data.averageAiDiffPercentage == null ? 'Brak wystarczających danych' : `${data.averageAiDiffPercentage.toFixed(1)}%`}
              />
              <StatTile label="Wyceny zmienione przez właściciela" value={formatPercent(data.percentQuotesChangedByOwner)} />
            </div>
          </Card>
        </>
      )}
    </main>
  )
}

export default DashboardPage
