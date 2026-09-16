import { useEffect, useState } from 'react'
import { getMyCompany, type CompanyResponse } from '../../shared/api/companyApi'
import { Alert, Button, Card } from '../../shared/ui'
import './EmbedWidgetPage.css'

function buildSnippet(company: CompanyResponse): string {
  const url = `${window.location.origin}/q/${company.slug}`
  return `<iframe
  src="${url}"
  title="Wycena AI - ${company.displayName}"
  style="width: 100%; max-width: 480px; height: 640px; border: none; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.12);"
></iframe>`
}

function EmbedWidgetPage() {
  const [company, setCompany] = useState<CompanyResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    getMyCompany()
      .then(setCompany)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać danych firmy'))
  }, [])

  async function handleCopy() {
    if (!company) return
    try {
      await navigator.clipboard.writeText(buildSnippet(company))
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard API unavailable — snippet is still selectable */
    }
  }

  return (
    <main className="embed-page">
      <div>
        <h1>Widget do Twojej strony</h1>
        <p className="embed-subtitle">
          Wklej poniższy kod na swojej stronie internetowej, aby klienci mogli uzyskać wycenę AI bezpośrednio na Twojej witrynie.
        </p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {company && (
        <>
          <Card>
            <h2>Kod do wklejenia</h2>
            <pre className="embed-snippet">{buildSnippet(company)}</pre>
            <Button size="sm" onClick={handleCopy}>
              {copied ? 'Skopiowano ✓' : 'Kopiuj kod'}
            </Button>
          </Card>

          <Card>
            <h2>Podgląd</h2>
            <p className="embed-subtitle">Tak będzie wyglądał widget osadzony na Twojej stronie.</p>
            <div className="embed-preview">
              <iframe
                src={`${window.location.origin}/q/${company.slug}`}
                title={`Podgląd widgetu — ${company.displayName}`}
                className="embed-preview-frame"
              />
            </div>
          </Card>
        </>
      )}
    </main>
  )
}

export default EmbedWidgetPage
