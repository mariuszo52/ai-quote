import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './app/AppLayout'
import RequireAuth from './app/RequireAuth'
import BillingPage from './app/pages/BillingPage'
import BrandingSettingsPage from './app/pages/BrandingSettingsPage'
import DashboardPage from './app/pages/DashboardPage'
import EmbedWidgetPage from './app/pages/EmbedWidgetPage'
import LeadDetailPage from './app/pages/LeadDetailPage'
import LeadsPage from './app/pages/LeadsPage'
import LoginPage from './app/pages/LoginPage'
import OnboardingPage from './app/pages/OnboardingPage'
import QuoteDetailPage from './app/pages/QuoteDetailPage'
import QuotesPage from './app/pages/QuotesPage'
import LandingPage from './marketing/pages/LandingPage'
import TermsPage from './marketing/pages/TermsPage'
import QuoteChatPage from './public-quote/pages/QuoteChatPage'
import PublicOfferPage from './public-quote/pages/PublicOfferPage'

function App() {
  return (
    <Routes>
      <Route path="/app/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/app/dashboard" element={<DashboardPage />} />
          <Route path="/app/onboarding" element={<OnboardingPage />} />
          <Route path="/app/leads" element={<LeadsPage />} />
          <Route path="/app/leads/:id" element={<LeadDetailPage />} />
          <Route path="/app/quotes" element={<QuotesPage />} />
          <Route path="/app/quotes/:id" element={<QuoteDetailPage />} />
          <Route path="/app/branding" element={<BrandingSettingsPage />} />
          <Route path="/app/embed" element={<EmbedWidgetPage />} />
          <Route path="/app/billing" element={<BillingPage />} />
          <Route path="/app" element={<Navigate to="/app/onboarding" replace />} />
        </Route>
      </Route>
      <Route path="/q/:slug" element={<QuoteChatPage />} />
      <Route path="/offer/:token" element={<PublicOfferPage />} />
      <Route path="/regulamin" element={<TermsPage />} />
      <Route path="/" element={<LandingPage />} />
    </Routes>
  )
}

export default App
