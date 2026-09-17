import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  completeOnboarding,
  getProfile,
  startSession,
  streamOnboardingMessage,
  type MessageDto,
  type ProfileResponse,
} from '../../shared/api/onboardingApi'
import { listSources, uploadSource, type KnowledgeSourceDto } from '../../shared/api/knowledgeApi'
import { Alert, Badge, Button, Card, ChatThread } from '../../shared/ui'
import PricingProfilePanel from './PricingProfilePanel'
import './OnboardingPage.css'

const SOURCE_STATUS_LABELS: Record<string, string> = {
  UPLOADED: 'w kolejce',
  PROCESSING: 'przetwarzanie...',
  PROCESSED: 'gotowe',
  FAILED: 'błąd',
}

const SOURCE_STATUS_TONE: Record<string, 'neutral' | 'warning' | 'success' | 'error'> = {
  UPLOADED: 'neutral',
  PROCESSING: 'warning',
  PROCESSED: 'success',
  FAILED: 'error',
}

function OnboardingPage() {
  const [conversationId, setConversationId] = useState<number | null>(null)
  const [messages, setMessages] = useState<MessageDto[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sources, setSources] = useState<KnowledgeSourceDto[]>([])
  const [uploading, setUploading] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [publicLink, setPublicLink] = useState<string | null>(null)
  const [linkCopied, setLinkCopied] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    startSession()
      .then((session) => {
        setConversationId(session.conversationId)
        setMessages(session.messages)
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się rozpocząć sesji'))

    getProfile()
      .then(setProfile)
      .catch(() => {
        /* profile not created yet — fine, stays empty until first AI update */
      })
  }, [])

  useEffect(() => {
    function refreshSources() {
      listSources()
        .then(setSources)
        .catch(() => {
          /* ignore transient polling failures */
        })
    }

    refreshSources()
    const timer = setInterval(refreshSources, 4000)
    return () => clearInterval(timer)
  }, [])

  async function handleSend(event: FormEvent) {
    event.preventDefault()
    if (!conversationId || !input.trim()) return

    const userMessage: MessageDto = { role: 'USER', content: input, createdAt: new Date().toISOString() }
    const content = input
    setMessages((prev) => [...prev, userMessage, { role: 'ASSISTANT', content: '', createdAt: new Date().toISOString() }])
    setInput('')
    setSending(true)
    setError(null)

    function appendDelta(chunk: string) {
      setMessages((prev) => {
        const next = [...prev]
        const last = next[next.length - 1]
        next[next.length - 1] = { ...last, content: last.content + chunk }
        return next
      })
    }

    await streamOnboardingMessage(conversationId, content, {
      onDelta: appendDelta,
      onDone: async (response) => {
        setSending(false)
        if (response.profileUpdated) {
          setProfile(await getProfile())
        }
      },
      onError: (message) => {
        setError(message)
        setSending(false)
      },
    })
  }

  async function handleFileUpload(event: FormEvent) {
    event.preventDefault()
    const file = fileInputRef.current?.files?.[0]
    if (!file) return

    setUploading(true)
    setError(null)
    try {
      await uploadSource(file)
      const updated = await listSources()
      setSources(updated)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się przesłać pliku')
    } finally {
      setUploading(false)
    }
  }

  async function handleComplete() {
    setCompleting(true)
    setError(null)
    try {
      const company = await completeOnboarding()
      setPublicLink(`${window.location.origin}/q/${company.slug}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zakończyć onboardingu')
    } finally {
      setCompleting(false)
    }
  }

  async function handleCopyLink() {
    if (!publicLink) return
    try {
      await navigator.clipboard.writeText(publicLink)
      setLinkCopied(true)
      setTimeout(() => setLinkCopied(false), 2000)
    } catch {
      /* clipboard API unavailable — link is still selectable/clickable */
    }
  }

  if (conversationId === null && !error) {
    return (
      <main className="onboarding-page">
        <p>Ładowanie...</p>
      </main>
    )
  }

  return (
    <main className="onboarding-page">
      <div className="onboarding-header">
        <h1>Wiedza firmy</h1>
        <p>
          Porozmawiaj z AI o swoich usługach i cenniku — im więcej wie, tym lepiej wyceni zlecenia klientów. Cennik jest{' '}
          <strong>opcjonalny</strong>. Jeśli w rozmowie wspomnisz o konkretnym materiale albo urządzeniu, którego używasz (np. rodzaj rury,
          model klimatyzatora), AI sam doda go do zakładki <Link to="/app/materials">Moje materiały</Link> — możesz tam też dodawać je
          ręcznie. Im więcej informacji w sumie podasz, tym dokładniejsza będzie wycena.
        </p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      <div className="onboarding-grid">
        <Card className="onboarding-chat-card" padded={false}>
          <ChatThread
            messages={messages}
            assistantLabel="AI"
            userLabel="Ty"
            emptyHint={<p style={{ color: 'var(--color-text-muted)' }}>Opowiedz AI o swojej firmie i o tym, jak zwykle wyceniasz zlecenia.</p>}
          />
          <form className="onboarding-composer" onSubmit={handleSend}>
            <input
              className="input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Opisz, jak wyceniasz swoje usługi..."
              disabled={sending}
            />
            <Button type="submit" disabled={!input.trim()} loading={sending}>
              Wyślij
            </Button>
          </form>
        </Card>

        <div className="onboarding-side">
          <PricingProfilePanel profile={profile} onSaved={setProfile} />

          <Card>
            <h2>Zakończenie onboardingu</h2>
            <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>
              Gdy wiedza cenowa (cennik i/lub materiały) jest gotowa, aktywuj publiczny link do wyceny dla klientów. Cennik nie jest
              wymagany — możesz aktywować link mając tylko uzupełnione materiały.
            </p>
            <Button size="sm" onClick={handleComplete} loading={completing} style={{ marginTop: 'var(--space-2)' }}>
              Zakończ onboarding
            </Button>
            {publicLink && (
              <Alert tone="success">
                <div>
                  Onboarding zakończony — publiczny link do wyceny jest aktywny.
                  <div className="onboarding-link-value">
                    <a href={publicLink} target="_blank" rel="noreferrer">
                      {publicLink}
                    </a>
                    <Button variant="secondary" size="sm" type="button" onClick={handleCopyLink}>
                      {linkCopied ? 'Skopiowano ✓' : 'Kopiuj'}
                    </Button>
                  </div>
                </div>
              </Alert>
            )}
          </Card>

          <Card>
            <h2>Cenniki i dokumenty</h2>
            <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)' }}>
              Prześlij istniejący cennik (PDF lub Excel) — AI wykorzysta go jako dodatkowe źródło wiedzy, a wymienione w nim materiały
              trafią też automatycznie do zakładki <Link to="/app/materials">Moje materiały</Link>.
            </p>
            <form className="onboarding-upload-row" onSubmit={handleFileUpload}>
              <input className="onboarding-file-input input" type="file" ref={fileInputRef} accept=".pdf,.xls,.xlsx" />
              <Button type="submit" size="sm" loading={uploading}>
                Prześlij
              </Button>
            </form>

            {sources.length === 0 ? (
              <p style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-muted)', marginTop: 'var(--space-3)' }}>
                Nie przesłano jeszcze żadnych dokumentów.
              </p>
            ) : (
              <div className="onboarding-sources-list">
                {sources.map((source) => (
                  <div className="onboarding-source-item" key={source.id}>
                    <span className="onboarding-source-name" title={source.originalFilename}>
                      {source.originalFilename}
                    </span>
                    <Badge tone={SOURCE_STATUS_TONE[source.status] ?? 'neutral'}>
                      {SOURCE_STATUS_LABELS[source.status] ?? source.status}
                    </Badge>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </main>
  )
}

export default OnboardingPage
