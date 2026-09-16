import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getLead, getLeadAttachmentUrl, updateLeadStatus, type LeadDetail } from '../../shared/api/leadApi'
import { allowedNextLeadStatuses, LEAD_STATUS_LABELS, LEAD_STATUS_TONE } from '../../shared/api/leadStatus'
import { getQuoteByLead, type QuoteResponse } from '../../shared/api/quotesApi'
import { Alert, Badge, Card, ChatThread, EmptyState, Spinner } from '../../shared/ui'
import './LeadDetailPage.css'

function LeadDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [lead, setLead] = useState<LeadDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [photoUrls, setPhotoUrls] = useState<Record<number, string>>({})
  const [openedPhoto, setOpenedPhoto] = useState<number | null>(null)
  const [updatingStatus, setUpdatingStatus] = useState(false)
  const [quote, setQuote] = useState<QuoteResponse | null>(null)

  useEffect(() => {
    if (!id) return
    getLead(Number(id))
      .then(setLead)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać leada'))

    getQuoteByLead(Number(id))
      .then(setQuote)
      .catch(() => {
        /* AI hasn't finished (or hasn't started) drafting a quote yet — nothing to show */
      })
  }, [id])

  useEffect(() => {
    if (!lead || !id || lead.attachments.length === 0) return

    let cancelled = false
    const urls: Record<number, string> = {}

    Promise.all(
      lead.attachments.map(async (attachment) => {
        try {
          const url = await getLeadAttachmentUrl(Number(id), attachment.id)
          urls[attachment.id] = url
        } catch {
          /* skip attachments that fail to load — rest of the page still renders */
        }
      }),
    ).then(() => {
      if (!cancelled) setPhotoUrls(urls)
    })

    return () => {
      cancelled = true
      Object.values(urls).forEach((url) => URL.revokeObjectURL(url))
    }
  }, [lead, id])

  async function handleStatusChange(status: string) {
    if (!id) return
    setUpdatingStatus(true)
    try {
      const updated = await updateLeadStatus(Number(id), status)
      setLead((prev) => (prev ? { ...prev, status: updated.status } : prev))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zaktualizować statusu')
    } finally {
      setUpdatingStatus(false)
    }
  }

  if (error && !lead) {
    return (
      <main className="lead-detail-page">
        <Alert tone="error">{error}</Alert>
      </main>
    )
  }

  if (!lead) {
    return (
      <main className="lead-detail-page" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <Spinner size="lg" />
      </main>
    )
  }

  return (
    <main className="lead-detail-page">
      <Link className="lead-detail-back" to="/app/leads">
        ← Wszystkie leady
      </Link>

      <div className="lead-detail-header">
        <div>
          <h1>{lead.clientName}</h1>
          <div className="lead-detail-contact">
            <span>Telefon: {lead.clientPhone}</span>
            {lead.clientEmail && <span>Email: {lead.clientEmail}</span>}
          </div>
        </div>
        <Badge tone={LEAD_STATUS_TONE[lead.status] ?? 'neutral'}>{LEAD_STATUS_LABELS[lead.status] ?? lead.status}</Badge>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      <div className="lead-detail-grid">
        <div className="lead-detail-main">
          <Card padded={false}>
            <ChatThread className="lead-transcript" messages={lead.transcript} assistantLabel="AI" userLabel="Klient" />
          </Card>

          <Card>
            <h2>Zdjęcia od klienta</h2>
            {lead.attachments.length === 0 ? (
              <EmptyState title="Brak zdjęć" description="Klient nie przesłał żadnych zdjęć w tej rozmowie." />
            ) : (
              <div className="photo-gallery-grid">
                {lead.attachments.map((attachment) =>
                  photoUrls[attachment.id] ? (
                    <button
                      key={attachment.id}
                      type="button"
                      className="photo-thumb"
                      onClick={() => setOpenedPhoto(attachment.id)}
                      title={attachment.filename}
                    >
                      <img src={photoUrls[attachment.id]} alt={attachment.filename} />
                    </button>
                  ) : (
                    <div key={attachment.id} className="photo-thumb" style={{ display: 'grid', placeItems: 'center' }}>
                      <Spinner size="sm" />
                    </div>
                  ),
                )}
              </div>
            )}
          </Card>
        </div>

        <div className="lead-side">
          <Card>
            <h2>Wycena</h2>
            <p className="lead-price">
              {lead.estimatedPriceMin}–{lead.estimatedPriceMax} {lead.currency}
            </p>
            {lead.aiSummary && <p style={{ marginTop: 'var(--space-3)' }}>{lead.aiSummary}</p>}
            {lead.uncertainNotes && (
              <>
                <h3 style={{ marginTop: 'var(--space-4)' }}>AI nie było pewne:</h3>
                <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>{lead.uncertainNotes}</p>
              </>
            )}
            {quote && (
              <p style={{ marginTop: 'var(--space-3)' }}>
                <Link to={`/app/quotes/${quote.id}`}>Zobacz szczegółową wycenę AI →</Link>
              </p>
            )}

            <div className="lead-status-row">
              <label className="field-label" htmlFor="lead-status">
                Status leada
              </label>
              {(() => {
                const nextStatuses = allowedNextLeadStatuses(lead.status)
                return (
                  <select
                    id="lead-status"
                    className="select"
                    value={lead.status}
                    disabled={updatingStatus || nextStatuses.length === 0}
                    onChange={(e) => handleStatusChange(e.target.value)}
                  >
                    <option value={lead.status}>{LEAD_STATUS_LABELS[lead.status] ?? lead.status}</option>
                    {nextStatuses.map((status) => (
                      <option key={status} value={status}>
                        {LEAD_STATUS_LABELS[status]}
                      </option>
                    ))}
                  </select>
                )
              })()}
            </div>
          </Card>
        </div>
      </div>

      {openedPhoto && photoUrls[openedPhoto] && (
        <div className="photo-lightbox" onClick={() => setOpenedPhoto(null)}>
          <img src={photoUrls[openedPhoto]} alt="Podgląd zdjęcia" />
        </div>
      )}
    </main>
  )
}

export default LeadDetailPage
