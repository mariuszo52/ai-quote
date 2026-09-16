import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { googleLogin, login, register } from '../../shared/api/authApi'
import { getToken } from '../../shared/api/client'
import { Alert, Button, TextField } from '../../shared/ui'
import './LoginPage.css'

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void
          renderButton: (parent: HTMLElement, options: { theme: string; size: string; width: string; text?: string }) => void
        }
      }
    }
  }
}

function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('register')
  const [companyName, setCompanyName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()
  const googleButtonRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined
    if (!clientId || !googleButtonRef.current) return

    let cancelled = false

    async function handleGoogleCredential(response: { credential: string }) {
      setError(null)
      try {
        await googleLogin(response.credential)
        navigate('/app/onboarding')
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Nie udało się zalogować przez Google.')
      }
    }

    // The GSI script is loaded with `defer`, so it may not be ready the instant this
    // component mounts — poll briefly instead of assuming it's already there. clientId
    // is passed explicitly (rather than closed over) so it stays typed as `string`
    // across the deferred setTimeout calls, not `string | undefined`.
    function tryRender(id: string, attemptsLeft: number) {
      if (cancelled) return
      if (window.google?.accounts?.id && googleButtonRef.current) {
        window.google.accounts.id.initialize({ client_id: id, callback: handleGoogleCredential })
        window.google.accounts.id.renderButton(googleButtonRef.current, { theme: 'outline', size: 'large', width: '100%' })
        return
      }
      if (attemptsLeft > 0) {
        setTimeout(() => tryRender(id, attemptsLeft - 1), 200)
      }
    }

    tryRender(clientId, 15)
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (getToken()) {
    return <Navigate to="/app" replace />
  }

  function switchMode(next: 'login' | 'register') {
    setMode(next)
    setError(null)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'register') {
        await register(companyName, email, password)
      } else {
        await login(email, password)
      }
      navigate('/app/onboarding')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Coś poszło nie tak. Spróbuj ponownie.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-brand-mark" aria-hidden="true" />
          <span className="auth-brand-name">AI Quote</span>
        </div>

        <div className="card card-padded">
          <div className="auth-tabs" role="tablist" aria-label="Logowanie lub rejestracja">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'register'}
              className={`auth-tab ${mode === 'register' ? 'auth-tab-active' : ''}`}
              onClick={() => switchMode('register')}
            >
              Załóż konto
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'login'}
              className={`auth-tab ${mode === 'login' ? 'auth-tab-active' : ''}`}
              onClick={() => switchMode('login')}
            >
              Zaloguj się
            </button>
          </div>

          <div className="auth-heading">
            <h1>{mode === 'register' ? 'Załóż firmę w AI Quote' : 'Witaj z powrotem'}</h1>
            <p>
              {mode === 'register'
                ? 'Skonfiguruj asystenta AI, który wyceni zlecenia za Ciebie.'
                : 'Zaloguj się, aby wrócić do panelu firmy.'}
            </p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit}>
            {mode === 'register' && (
              <TextField
                label="Nazwa firmy"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                placeholder="np. Elektryk Kowalski"
                required
                autoFocus
              />
            )}
            <TextField
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="ty@firma.pl"
              autoComplete="email"
              required
            />
            <TextField
              label="Hasło"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              hint={mode === 'register' ? 'Minimum 8 znaków' : undefined}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              required
              minLength={8}
            />

            {error && <Alert tone="error">{error}</Alert>}

            <Button type="submit" loading={submitting} fullWidth>
              {mode === 'register' ? 'Zarejestruj i przejdź do onboardingu' : 'Zaloguj się'}
            </Button>
          </form>

          <div className="auth-divider">
            <span>lub</span>
          </div>

          <div ref={googleButtonRef} className="auth-google-button" />
        </div>
      </div>
    </main>
  )
}

export default LoginPage
