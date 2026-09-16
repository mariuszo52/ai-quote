import { useEffect, useState } from 'react'
import {
  getBillingStatus,
  openBillingPortal,
  startCheckout,
  type BillingStatus,
  type CompanyPlanValue,
} from '../../shared/api/billingApi'
import { Alert, Button, Card } from '../../shared/ui'
import './BillingPage.css'

interface PlanOption {
  plan: Exclude<CompanyPlanValue, 'TRIAL'>
  name: string
  price: string
  quotes: string
}

const PLANS: PlanOption[] = [
  { plan: 'STARTER', name: 'Starter', price: '49 zł/mies.', quotes: '15 wycen / miesiąc' },
  { plan: 'GROWTH', name: 'Growth', price: '129 zł/mies.', quotes: '50 wycen / miesiąc' },
  { plan: 'PRO', name: 'Pro', price: '299 zł/mies.', quotes: '150 wycen / miesiąc' },
]

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('pl-PL')
}

function BillingPage() {
  const [status, setStatus] = useState<BillingStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyPlan, setBusyPlan] = useState<CompanyPlanValue | 'portal' | null>(null)

  useEffect(() => {
    getBillingStatus()
      .then(setStatus)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać danych rozliczeniowych'))
  }, [])

  async function handleChoosePlan(plan: Exclude<CompanyPlanValue, 'TRIAL'>) {
    setBusyPlan(plan)
    setError(null)
    try {
      const url = await startCheckout(plan)
      // eslint-disable-next-line react/immutability -- intentional external redirect to Stripe Checkout, not a stray mutation
      window.location.href = url
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się rozpocząć płatności')
      setBusyPlan(null)
    }
  }

  async function handleManageBilling() {
    setBusyPlan('portal')
    setError(null)
    try {
      const url = await openBillingPortal()
      // eslint-disable-next-line react/immutability -- intentional external redirect to the Stripe Billing Portal, not a stray mutation
      window.location.href = url
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się otworzyć panelu rozliczeń')
      setBusyPlan(null)
    }
  }

  return (
    <main className="billing-page">
      <div>
        <h1>Plan i rozliczenia</h1>
        <p className="billing-subtitle">Zarządzaj planem subskrypcji i sprawdź wykorzystanie wycen.</p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {status && (
        <>
          <Card>
            <h2>Obecny plan: {status.planDisplayName}</h2>
            <div className="billing-tile-grid">
              <div className="billing-tile">
                <span className="billing-tile-label">Wykorzystane wyceny</span>
                <span className="billing-tile-value">
                  {status.quotesUsed} / {status.quoteLimit}
                </span>
              </div>
              <div className="billing-tile">
                <span className="billing-tile-label">{status.plan === 'TRIAL' ? 'Koniec okresu próbnego' : 'Koniec okresu rozliczeniowego'}</span>
                <span className="billing-tile-value">{formatDate(status.plan === 'TRIAL' ? status.trialEndsAt : status.currentPeriodEnd)}</span>
              </div>
            </div>
            {status.canManageBilling && (
              <Button variant="secondary" onClick={handleManageBilling} loading={busyPlan === 'portal'} style={{ marginTop: 'var(--space-4)' }}>
                Zarządzaj subskrypcją
              </Button>
            )}
          </Card>

          <Card>
            <h2>Pakiety</h2>
            <div className="billing-plans-grid">
              {PLANS.map((option) => (
                <div key={option.plan} className={`billing-plan-card ${status.plan === option.plan ? 'billing-plan-card-active' : ''}`}>
                  <h3>{option.name}</h3>
                  <div className="billing-plan-price">{option.price}</div>
                  <p className="billing-plan-quotes">{option.quotes}</p>
                  <Button
                    onClick={() => handleChoosePlan(option.plan)}
                    loading={busyPlan === option.plan}
                    disabled={status.plan === option.plan}
                    fullWidth
                  >
                    {status.plan === option.plan ? 'Aktualny plan' : 'Wybierz'}
                  </Button>
                </div>
              ))}
            </div>
          </Card>
        </>
      )}
    </main>
  )
}

export default BillingPage
