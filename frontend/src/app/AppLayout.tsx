import { useEffect, useState } from 'react'
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { getBillingStatus } from '../shared/api/billingApi'
import { clearToken } from '../shared/api/client'
import { Alert, Button } from '../shared/ui'
import './AppLayout.css'

const BILLING_PATH = '/app/billing'

function navLinkClass({ isActive }: { isActive: boolean }) {
  return `app-nav-link ${isActive ? 'app-nav-link-active' : ''}`
}

function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [blocked, setBlocked] = useState(false)

  // Fetched once per app-shell mount, not per route — the trial/plan limit can only
  // change via an owner action (checkout) or the passage of time, neither of which
  // needs sub-second freshness. Failing to fetch leaves `blocked` false (fail open):
  // a billing-status hiccup must never lock an owner out of their own account.
  useEffect(() => {
    getBillingStatus()
      .then((status) => setBlocked(status.blocked))
      .catch(() => {
        /* fail open, see above */
      })
  }, [])

  function handleLogout() {
    clearToken()
    navigate('/app/login', { replace: true })
  }

  // Once the trial window or the plan's quote allowance runs out, every feature except
  // plan selection itself is off-limits — redirect anything else straight to billing so
  // the owner can upgrade, rather than letting them poke around a half-working app.
  if (blocked && location.pathname !== BILLING_PATH) {
    return <Navigate to={BILLING_PATH} replace />
  }

  return (
    <div className="app-shell">
      <header className="app-topbar">
        <div className="app-brand">
          <span className="app-brand-mark" aria-hidden="true" />
          <span className="app-brand-name">AI Quote</span>
        </div>
        {!blocked && (
          <nav className="app-nav">
            <NavLink to="/app/dashboard" className={navLinkClass}>
              Statystyki
            </NavLink>
            <NavLink to="/app/onboarding" className={navLinkClass}>
              Wiedza firmy
            </NavLink>
            <NavLink to="/app/leads" className={navLinkClass}>
              Leady
            </NavLink>
            <NavLink to="/app/quotes" className={navLinkClass}>
              Wyceny
            </NavLink>
            <NavLink to="/app/branding" className={navLinkClass}>
              Wygląd firmy
            </NavLink>
            <NavLink to="/app/embed" className={navLinkClass}>
              Widget
            </NavLink>
            <NavLink to="/app/billing" className={navLinkClass}>
              Plan i rozliczenia
            </NavLink>
          </nav>
        )}
        <Button variant="ghost" size="sm" onClick={handleLogout}>
          Wyloguj się
        </Button>
      </header>
      <div className="app-content">
        {blocked && (
          <Alert tone="error">
            Okres próbny się zakończył albo wykorzystano limit wycen w bieżącym okresie. Wybierz plan poniżej, aby dalej korzystać z AI Quote.
          </Alert>
        )}
        <Outlet />
      </div>
    </div>
  )
}

export default AppLayout
