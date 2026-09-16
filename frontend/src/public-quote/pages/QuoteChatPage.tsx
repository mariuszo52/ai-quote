import { useEffect, useRef, useState, type ChangeEvent, type CSSProperties, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import {
  companyLogoUrl,
  getCompanyPublicInfo,
  startConversation,
  streamQuoteMessage,
  submitContact,
  uploadAttachment,
  type CompanyPublicInfo,
  type QuoteDto,
} from '../../shared/api/publicQuoteApi'
import { ApiError } from '../../shared/api/client'
import { Alert, Button, Card, ChatThread, EmptyState, Spinner, TextField } from '../../shared/ui'
import './QuoteChatPage.css'

interface ChatMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
}

interface PendingAttachment {
  localId: string
  filename: string
  previewUrl: string
  status: 'uploading' | 'done' | 'error'
}

type PageStatus = 'loading' | 'not-found' | 'not-ready' | 'limit-exceeded' | 'ready'

let localIdCounter = 0
function nextLocalId() {
  localIdCounter += 1
  return `att-${localIdCounter}`
}

function QuoteChatPage() {
  const { slug } = useParams<{ slug: string }>()
  const [status, setStatus] = useState<PageStatus>('loading')
  const [company, setCompany] = useState<CompanyPublicInfo | null>(null)
  const [conversationId, setConversationId] = useState<number | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [analyzingImages, setAnalyzingImages] = useState(false)
  const [quote, setQuote] = useState<QuoteDto | null>(null)
  const [options, setOptions] = useState<string[]>([])
  const [contactName, setContactName] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [submittingContact, setSubmittingContact] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pendingAttachments, setPendingAttachments] = useState<PendingAttachment[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!slug) return

    getCompanyPublicInfo(slug)
      .then(async (info) => {
        setCompany(info)
        if (!info.ready) {
          setStatus('not-ready')
          return
        }
        const session = await startConversation(slug)
        setConversationId(session.conversationId)
        setToken(session.token)
        setMessages([{ role: 'ASSISTANT', content: session.greeting }])
        setOptions(session.options)
        setStatus('ready')
      })
      .catch((err: unknown) => {
        // 409 = the company has used up its trial/plan quote allowance for this
        // period (see QuoteAgentService.assertWithinPlanLimit) — a distinct, expected
        // state from "no such company", not a generic error.
        setStatus(err instanceof ApiError && err.status === 409 ? 'limit-exceeded' : 'not-found')
      })
  }, [slug])

  function clearPendingAttachments() {
    setPendingAttachments((prev) => {
      prev.forEach((attachment) => URL.revokeObjectURL(attachment.previewUrl))
      return []
    })
  }

  async function sendContent(conversationIdValue: number, tokenValue: string, content: string) {
    const hadImages = pendingAttachments.some((attachment) => attachment.status === 'done')
    const userMessage: ChatMessage = { role: 'USER', content }
    setMessages((prev) => [...prev, userMessage, { role: 'ASSISTANT', content: '' }])
    setOptions([])
    setSending(true)
    setAnalyzingImages(hadImages)
    setError(null)

    function appendDelta(chunk: string) {
      setMessages((prev) => {
        const next = [...prev]
        const last = next[next.length - 1]
        next[next.length - 1] = { ...last, content: last.content + chunk }
        return next
      })
    }

    await streamQuoteMessage(conversationIdValue, tokenValue, content, {
      onDelta: (chunk) => {
        setAnalyzingImages(false)
        appendDelta(chunk)
      },
      onDone: (response) => {
        setSending(false)
        setAnalyzingImages(false)
        clearPendingAttachments()
        setOptions(response.options)
        if (response.quote) {
          setQuote(response.quote)
        }
      },
      onError: (message) => {
        setError(message)
        setSending(false)
        setAnalyzingImages(false)
      },
    })
  }

  async function handleSend(event: FormEvent) {
    event.preventDefault()
    if (!conversationId || !token || !input.trim()) return
    const content = input
    setInput('')
    await sendContent(conversationId, token, content)
  }

  async function handleOptionClick(option: string) {
    if (!conversationId || !token || sending) return
    await sendContent(conversationId, token, option)
  }

  function handleAttachClick() {
    fileInputRef.current?.click()
  }

  async function handleFilesSelected(event: ChangeEvent<HTMLInputElement>) {
    const inputEl = event.currentTarget
    const files = inputEl.files ? Array.from(inputEl.files) : []
    inputEl.value = ''
    if (files.length === 0 || !conversationId || !token) return

    setError(null)
    for (const file of files) {
      const localId = nextLocalId()
      const previewUrl = URL.createObjectURL(file)
      setPendingAttachments((prev) => [...prev, { localId, filename: file.name, previewUrl, status: 'uploading' }])

      try {
        await uploadAttachment(conversationId, token, file)
        setPendingAttachments((prev) => prev.map((a) => (a.localId === localId ? { ...a, status: 'done' } : a)))
      } catch {
        setPendingAttachments((prev) => prev.map((a) => (a.localId === localId ? { ...a, status: 'error' } : a)))
        setError('Nie udało się przesłać jednego ze zdjęć.')
      }
    }
  }

  async function handleSubmitContact(event: FormEvent) {
    event.preventDefault()
    if (!conversationId || !token) return

    setSubmittingContact(true)
    setError(null)
    try {
      await submitContact(conversationId, token, {
        name: contactName,
        phone: contactPhone,
        email: contactEmail || undefined,
      })
      setSubmitted(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się wysłać zgłoszenia')
    } finally {
      setSubmittingContact(false)
    }
  }

  if (status === 'loading') {
    return (
      <main className="quote-page">
        <div className="quote-shell" style={{ alignItems: 'center', justifyContent: 'center' }}>
          <Spinner size="lg" />
        </div>
      </main>
    )
  }

  if (status === 'not-found') {
    return (
      <main className="quote-page">
        <div className="quote-shell quote-not-found">
          <EmptyState title="Nie znaleziono takiej firmy" description="Sprawdź, czy link do wyceny jest poprawny." />
        </div>
      </main>
    )
  }

  if (status === 'not-ready') {
    return (
      <main className="quote-page">
        <div className="quote-shell quote-not-ready">
          <EmptyState title={company?.displayName ?? ''} description="Ta firma jeszcze nie ukończyła konfiguracji wyceny AI. Spróbuj ponownie później." />
        </div>
      </main>
    )
  }

  if (status === 'limit-exceeded') {
    return (
      <main className="quote-page">
        <div className="quote-shell quote-not-ready">
          <EmptyState
            title={company?.displayName ?? ''}
            description="Ta firma tymczasowo nie przyjmuje nowych zapytań. Spróbuj ponownie później."
          />
        </div>
      </main>
    )
  }

  const canUpload = pendingAttachments.every((a) => a.status !== 'uploading')
  const companyName = company?.displayName ?? ''
  const primaryColor = company?.primaryColor

  return (
    <main className="quote-page" style={primaryColor ? ({ '--color-primary': primaryColor } as CSSProperties) : undefined}>
      <div className="quote-shell">
        <header className="quote-header">
          {company?.hasLogo && slug ? (
            <img className="quote-header-logo" src={companyLogoUrl(slug)} alt={companyName} />
          ) : (
            <span className="quote-header-avatar" aria-hidden="true">
              {companyName.charAt(0).toUpperCase()}
            </span>
          )}
          <div>
            <div className="quote-header-title">{companyName}</div>
            <div className="quote-header-subtitle">{company?.welcomeText}</div>
          </div>
        </header>

        <div className="quote-body">
          <ChatThread
            messages={messages}
            assistantLabel={companyName}
            userLabel="Ty"
            emptyHint={<p style={{ color: 'var(--color-text-muted)' }}>Opisz, czego potrzebujesz, a AI przygotuje wstępną wycenę.</p>}
          />

          {analyzingImages && (
            <div className="quote-analyzing">
              <Spinner size="sm" />
              <span>AI analizuje przesłane zdjęcia...</span>
            </div>
          )}

          {options.length > 0 && !sending && (
            <div className="quote-options">
              {options.map((option) => (
                <button key={option} type="button" className="chip" onClick={() => handleOptionClick(option)}>
                  {option}
                </button>
              ))}
            </div>
          )}

          {pendingAttachments.length > 0 && (
            <div className="quote-attachments-preview">
              {pendingAttachments.map((attachment) => (
                <div
                  key={attachment.localId}
                  className={`quote-attachment-thumb ${attachment.status === 'error' ? 'quote-attachment-thumb-error' : ''}`}
                  title={attachment.filename}
                >
                  <img src={attachment.previewUrl} alt={attachment.filename} />
                  {attachment.status === 'uploading' && (
                    <span className="quote-attachment-thumb-overlay">
                      <Spinner size="sm" />
                    </span>
                  )}
                  {attachment.status === 'error' && <span className="quote-attachment-thumb-overlay">⚠</span>}
                </div>
              ))}
            </div>
          )}

          {error && (
            <div className="quote-panel" style={{ paddingTop: 0 }}>
              <Alert tone="error">{error}</Alert>
            </div>
          )}

          {quote && !submitted && (
            <div className="quote-panel">
              <Card>
                <h2>Wstępna wycena</h2>
                <p>
                  <span className="quote-result-price">
                    {quote.minPrice}–{quote.maxPrice}
                  </span>{' '}
                  <span className="quote-result-currency">{quote.currency}</span>
                </p>
                <p style={{ marginTop: 'var(--space-2)' }}>{quote.reasoning}</p>

                {quote.uncertainFactors.length > 0 && (
                  <>
                    <p style={{ marginTop: 'var(--space-3)', fontWeight: 600, color: 'var(--color-text)' }}>
                      Warto doprecyzować:
                    </p>
                    <ul className="quote-uncertain-list">
                      {quote.uncertainFactors.map((factor, index) => (
                        <li key={index}>{factor}</li>
                      ))}
                    </ul>
                  </>
                )}

                <h3 style={{ marginTop: 'var(--space-5)' }}>Zostaw dane kontaktowe, żeby otrzymać ofertę</h3>
                <form className="quote-contact-form" onSubmit={handleSubmitContact}>
                  <TextField label="Imię i nazwisko" value={contactName} onChange={(e) => setContactName(e.target.value)} required />
                  <TextField label="Telefon" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} required />
                  <TextField
                    label="Email (opcjonalnie)"
                    type="email"
                    value={contactEmail}
                    onChange={(e) => setContactEmail(e.target.value)}
                  />
                  <Button type="submit" loading={submittingContact}>
                    Wyślij zapytanie
                  </Button>
                </form>
              </Card>
            </div>
          )}

          {submitted && (
            <div className="quote-panel">
              <Card className="quote-success">
                <h2>Dziękujemy!</h2>
                <p>Firma skontaktuje się z Tobą wkrótce.</p>
              </Card>
            </div>
          )}
        </div>

        {!submitted && (
          <form className="quote-composer" onSubmit={handleSend}>
            <div className="quote-composer-row">
              <button
                type="button"
                className="btn btn-secondary quote-attach-btn"
                onClick={handleAttachClick}
                disabled={!canUpload}
                title="Dodaj zdjęcie"
                aria-label="Dodaj zdjęcie"
              >
                📷
              </button>
              <input
                className="input quote-composer-input"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Opisz swoje zlecenie..."
                disabled={sending}
              />
              <Button type="submit" disabled={sending || !input.trim()} loading={sending && !analyzingImages}>
                Wyślij
              </Button>
            </div>
            <input
              type="file"
              ref={fileInputRef}
              accept="image/jpeg,image/png,image/webp"
              multiple
              hidden
              onChange={handleFilesSelected}
            />
          </form>
        )}
      </div>
    </main>
  )
}

export default QuoteChatPage
