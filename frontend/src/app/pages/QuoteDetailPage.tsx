import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { FEEDBACK_REASON_LABELS, getQuoteFeedback, submitQuoteFeedback, type FeedbackReason, type QuoteFeedbackResponse } from '../../shared/api/feedbackApi'
import { getLead, getLeadAttachmentUrl, type LeadDetail } from '../../shared/api/leadApi'
import { getOffer, getOfferPdfUrl, sendOffer, type OfferResponse } from '../../shared/api/offerApi'
import {
  approveQuote,
  getQuote,
  getQuotePdfUrl,
  updateQuote,
  type QuoteLineItem,
  type QuoteResponse,
} from '../../shared/api/quotesApi'
import { QUOTE_STATUS_LABELS, QUOTE_STATUS_TONE } from '../../shared/api/quoteStatus'
import { Alert, Badge, Button, Card, ChatThread, EmptyState, Spinner, TextAreaField, TextField } from '../../shared/ui'
import './QuoteDetailPage.css'

function blankItem(): QuoteLineItem {
  return { name: '', description: null, quantity: 1, unit: null, unitPrice: null, totalPrice: null, source: 'manual' }
}

function previewTotal(items: QuoteLineItem[]): number {
  return items.reduce((sum, item) => {
    if (item.quantity == null || item.unitPrice == null) return sum
    return sum + item.quantity * item.unitPrice
  }, 0)
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('pl-PL', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function confidenceTone(confidence: number | null): 'success' | 'warning' | 'error' | 'neutral' {
  if (confidence == null) return 'neutral'
  if (confidence >= 0.7) return 'success'
  if (confidence >= 0.4) return 'warning'
  return 'error'
}

function QuoteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [quote, setQuote] = useState<QuoteResponse | null>(null)
  const [lead, setLead] = useState<LeadDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [photoUrls, setPhotoUrls] = useState<Record<number, string>>({})
  const [openedPhoto, setOpenedPhoto] = useState<number | null>(null)

  const [draftItems, setDraftItems] = useState<QuoteLineItem[]>([])
  const [draftClientNote, setDraftClientNote] = useState('')
  const [draftInternalNote, setDraftInternalNote] = useState('')
  const [draftEstimatedTimeline, setDraftEstimatedTimeline] = useState('')
  const [draftOfferValidUntil, setDraftOfferValidUntil] = useState('')
  const [saving, setSaving] = useState(false)
  const [justSaved, setJustSaved] = useState(false)
  const [showApproveConfirm, setShowApproveConfirm] = useState(false)
  const [approving, setApproving] = useState(false)
  const [downloadingPdf, setDownloadingPdf] = useState(false)
  const [offer, setOffer] = useState<OfferResponse | null>(null)
  const [downloadingOfferPdf, setDownloadingOfferPdf] = useState(false)
  const [sendingOffer, setSendingOffer] = useState(false)
  const [feedback, setFeedback] = useState<QuoteFeedbackResponse | null>(null)
  const [feedbackReason, setFeedbackReason] = useState<FeedbackReason | ''>('')
  const [feedbackNote, setFeedbackNote] = useState('')
  const [savingFeedback, setSavingFeedback] = useState(false)
  const [feedbackSaved, setFeedbackSaved] = useState(false)

  useEffect(() => {
    if (!id) return
    getQuote(Number(id))
      .then(setQuote)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać wyceny'))
  }, [id])

  // Re-sync editable draft state whenever we get a fresh quote (initial load, or right
  // after our own save/approve response) — never while the owner is mid-edit, since
  // `quote` only changes through those two paths.
  useEffect(() => {
    if (!quote) return
    setDraftItems(quote.items.length > 0 ? quote.items : [blankItem()])
    setDraftClientNote(quote.clientNote ?? '')
    setDraftInternalNote(quote.internalNote ?? '')
    setDraftEstimatedTimeline(quote.estimatedTimeline ?? '')
    setDraftOfferValidUntil(quote.offerValidUntil ?? '')
  }, [quote])

  // Etap 12: once approved, a final Offer already exists (generated atomically with the
  // approval itself) — fetch it so the "Oferta przygotowana" panel can render.
  useEffect(() => {
    if (!quote || quote.approvedAt == null) return
    getOffer(quote.id)
      .then(setOffer)
      .catch(() => {
        /* offer generation failing would have failed the approval itself — this is just a fetch hiccup */
      })
  }, [quote])

  // Etap 15: same idea — the AI-vs-final comparison row is generated atomically with
  // approval, so it's always there to fetch once the quote is approved. Gated on
  // approvedAt rather than status === 'APPROVED', since a later send success/failure
  // moves status to SENT_TO_CLIENT/SEND_FAILED without un-approving the quote (mirrors
  // the backend's own approvedAt != null check in QuoteService.update).
  useEffect(() => {
    if (!quote || quote.approvedAt == null) return
    getQuoteFeedback(quote.id)
      .then((data) => {
        setFeedback(data)
        setFeedbackReason(data.reason ?? '')
        setFeedbackNote(data.note ?? '')
      })
      .catch(() => {
        /* same as offer above — a fetch hiccup, not a hard failure */
      })
  }, [quote])

  // Deliberately keyed on leadId (not the whole `quote` object) — leadId never changes
  // for a given quote, so this avoids refetching the transcript/photos after every save.
  useEffect(() => {
    if (!quote) return
    getLead(quote.leadId)
      .then(setLead)
      .catch(() => {
        /* transcript/photos are a bonus — the quote itself still renders without them */
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [quote?.leadId])

  useEffect(() => {
    if (!lead || lead.attachments.length === 0) return

    let cancelled = false
    const urls: Record<number, string> = {}

    Promise.all(
      lead.attachments.map(async (attachment) => {
        try {
          urls[attachment.id] = await getLeadAttachmentUrl(lead.id, attachment.id)
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
  }, [lead])

  function updateDraftItem(index: number, patch: Partial<QuoteLineItem>) {
    setDraftItems((prev) => prev.map((item, i) => (i === index ? { ...item, ...patch } : item)))
  }

  function removeDraftItem(index: number) {
    setDraftItems((prev) => prev.filter((_, i) => i !== index))
  }

  function addDraftItem() {
    setDraftItems((prev) => [...prev, blankItem()])
  }

  async function handleSave() {
    if (!quote) return
    setSaving(true)
    setError(null)
    try {
      const updated = await updateQuote(quote.id, {
        items: draftItems.filter((item) => item.name.trim().length > 0),
        clientNote: draftClientNote.trim() || null,
        internalNote: draftInternalNote.trim() || null,
        estimatedTimeline: draftEstimatedTimeline.trim() || null,
        offerValidUntil: draftOfferValidUntil || null,
      })
      setQuote(updated)
      setJustSaved(true)
      setTimeout(() => setJustSaved(false), 2500)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zapisać zmian')
    } finally {
      setSaving(false)
    }
  }

  async function handleConfirmApprove() {
    if (!quote) return
    setApproving(true)
    setError(null)
    try {
      const updated = await approveQuote(quote.id)
      setQuote(updated)
      setShowApproveConfirm(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zaakceptować wyceny')
    } finally {
      setApproving(false)
    }
  }

  async function handleDownloadPdf() {
    if (!quote) return
    setDownloadingPdf(true)
    setError(null)
    try {
      const url = await getQuotePdfUrl(quote.id)
      const link = document.createElement('a')
      link.href = url
      link.download = `wycena-${quote.id}.pdf`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się pobrać PDF wyceny')
    } finally {
      setDownloadingPdf(false)
    }
  }

  async function handleDownloadOfferPdf() {
    if (!quote) return
    setDownloadingOfferPdf(true)
    setError(null)
    try {
      const url = await getOfferPdfUrl(quote.id)
      const link = document.createElement('a')
      link.href = url
      link.download = `oferta-${quote.id}.pdf`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się pobrać PDF oferty')
    } finally {
      setDownloadingOfferPdf(false)
    }
  }

  async function handleSendOffer() {
    if (!quote) return
    setSendingOffer(true)
    setError(null)
    try {
      const updated = await sendOffer(quote.id)
      setOffer(updated)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się wysłać oferty do klienta')
    } finally {
      setSendingOffer(false)
    }
  }

  async function handleSaveFeedback() {
    if (!quote) return
    setSavingFeedback(true)
    setError(null)
    try {
      const updated = await submitQuoteFeedback(quote.id, feedbackReason || null, feedbackNote.trim() || null)
      setFeedback(updated)
      setFeedbackSaved(true)
      setTimeout(() => setFeedbackSaved(false), 2500)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zapisać opinii')
    } finally {
      setSavingFeedback(false)
    }
  }

  if (error && !quote) {
    return (
      <main className="quote-detail-page">
        <Alert tone="error">{error}</Alert>
      </main>
    )
  }

  if (!quote) {
    return (
      <main className="quote-detail-page" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <Spinner size="lg" />
      </main>
    )
  }

  // Mirrors the backend's own approvedAt != null check (QuoteService.update) rather than
  // status === 'APPROVED': once approved, status keeps moving (SENT_TO_CLIENT,
  // SEND_FAILED) as the offer is sent/retried, but the quote stays locked/approved the
  // whole time — using raw status here would hide the offer panel (and the retry-send
  // button) again after a page revisit following a send success or failure.
  const isApproved = quote.approvedAt != null
  const isEdited = quote.status === 'OWNER_EDITED' || quote.total !== quote.aiTotal
  const diff = quote.total - quote.aiTotal

  return (
    <main className="quote-detail-page">
      <Link className="quote-detail-back" to="/app/quotes">
        ← Wszystkie wyceny
      </Link>

      <div className="quote-detail-header">
        <div>
          <h1>Wycena #{quote.id}</h1>
          <div className="quote-detail-contact">
            <span>Klient: {quote.clientName ?? '—'}</span>
            {quote.clientPhone && <span>Telefon: {quote.clientPhone}</span>}
            {quote.clientEmail && <span>Email: {quote.clientEmail}</span>}
          </div>
        </div>
        <div className="quote-detail-header-actions">
          <Badge tone={QUOTE_STATUS_TONE[quote.status] ?? 'neutral'}>{QUOTE_STATUS_LABELS[quote.status] ?? quote.status}</Badge>
          <Button variant="secondary" size="sm" onClick={handleDownloadPdf} loading={downloadingPdf}>
            Pobierz PDF wyceny
          </Button>
        </div>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {isApproved && quote.approvedAt && <Alert tone="success">Wycena zaakceptowana {formatDateTime(quote.approvedAt)}. Edycja jest zablokowana.</Alert>}

      {isApproved && offer && (
        <Card>
          {offer.status === 'SENT' ? (
            <>
              <h2>Oferta wysłana do klienta</h2>
              {offer.sentAt && <p style={{ color: 'var(--color-text-muted)' }}>Wysłano {formatDateTime(offer.sentAt)}.</p>}
            </>
          ) : (
            <>
              <h2>Oferta gotowa</h2>
              <p style={{ color: 'var(--color-text-muted)' }}>
                Finalna oferta dla klienta (oferta nr {offer.id}) została wygenerowana na podstawie zaakceptowanej wyceny.
              </p>
              {offer.status === 'SEND_FAILED' && (
                <Alert tone="error">Wysyłka nie powiodła się{offer.lastSendError ? `: ${offer.lastSendError}` : '.'} Możesz spróbować ponownie.</Alert>
              )}
            </>
          )}
          <div className="quote-panel-actions">
            <a className="btn btn-secondary" href={`/offer/${offer.publicToken}`} target="_blank" rel="noreferrer">
              Podgląd
            </a>
            <Button variant="secondary" onClick={handleDownloadOfferPdf} loading={downloadingOfferPdf}>
              Pobierz PDF
            </Button>
            <Button variant={offer.status === 'SENT' ? 'secondary' : 'primary'} onClick={handleSendOffer} loading={sendingOffer}>
              {offer.status === 'SENT' ? '📧 Wyślij ponownie' : '📧 Wyślij do klienta'}
            </Button>
          </div>
        </Card>
      )}

      {isApproved && feedback && (
        <Card>
          <h2>Jak oceniasz wycenę AI?</h2>
          <div className="quote-feedback-comparison">
            <div>
              <span className="field-label">AI zaproponowało</span>
              <p>
                {feedback.aiTotal} {quote.currency}
              </p>
            </div>
            <div>
              <span className="field-label">Twoja finalna cena</span>
              <p>
                {feedback.finalTotal} {quote.currency}
              </p>
            </div>
          </div>

          {feedback.diffAmount === 0 ? (
            <p style={{ color: 'var(--color-text-muted)' }}>Zaakceptowałeś wycenę AI bez zmian.</p>
          ) : (
            <>
              <div className="quote-notes-grid">
                <div>
                  <label className="field-label" htmlFor="feedback-reason">
                    Powód zmiany
                  </label>
                  <select
                    id="feedback-reason"
                    className="select"
                    value={feedbackReason}
                    onChange={(e) => setFeedbackReason(e.target.value as FeedbackReason | '')}
                  >
                    <option value="">— nie podano —</option>
                    {(Object.keys(FEEDBACK_REASON_LABELS) as FeedbackReason[]).map((reason) => (
                      <option key={reason} value={reason}>
                        {FEEDBACK_REASON_LABELS[reason]}
                      </option>
                    ))}
                  </select>
                </div>
                <TextAreaField label="Notatka (opcjonalnie)" value={feedbackNote} onChange={(e) => setFeedbackNote(e.target.value)} />
              </div>
              <div className="quote-panel-actions">
                <Button variant="secondary" onClick={handleSaveFeedback} loading={savingFeedback}>
                  Zapisz
                </Button>
                {feedbackSaved && <span className="quote-save-status">Zapisano ✓</span>}
              </div>
            </>
          )}
        </Card>
      )}

      <div className="quote-detail-grid">
        <div className="quote-detail-main">
          {lead && (
            <Card padded={false}>
              <ChatThread className="quote-transcript" messages={lead.transcript} assistantLabel="AI" userLabel="Klient" />
            </Card>
          )}

          <Card>
            <h2>Zdjęcia od klienta</h2>
            {!lead || lead.attachments.length === 0 ? (
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

          <Card>
            <h2>Ocena AI</h2>
            <div className="quote-confidence-row">
              <span>Pewność wyceny:</span>
              <Badge tone={confidenceTone(quote.aiConfidence)}>
                {quote.aiConfidence != null ? `${Math.round(quote.aiConfidence * 100)}%` : 'nieznana'}
              </Badge>
            </div>
            {quote.aiReasoning && <p style={{ marginTop: 'var(--space-3)' }}>{quote.aiReasoning}</p>}

            {quote.uncertainFactors.length > 0 && (
              <>
                <h3 style={{ marginTop: 'var(--space-4)' }}>Wymaga uwagi:</h3>
                <ul className="quote-uncertain-list">
                  {quote.uncertainFactors.map((factor, index) => (
                    <li key={index}>{factor}</li>
                  ))}
                </ul>
              </>
            )}

            {quote.changeLog.length > 0 && (
              <>
                <h3 style={{ marginTop: 'var(--space-4)' }}>Historia zmian</h3>
                <ul className="quote-changelog-list">
                  {quote.changeLog.map((entry, index) => (
                    <li key={index}>
                      <span className="quote-changelog-date">{formatDateTime(entry.createdAt)}</span> — {entry.summary}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Card>
        </div>

        <div className="quote-detail-side">
          <Card>
            <div className="quote-panel-header">
              <h2>Pozycje wyceny</h2>
              {justSaved && <span className="quote-save-status">Zapisano ✓</span>}
            </div>

            {isApproved ? (
              <table className="data-table quote-items-table">
                <thead>
                  <tr>
                    <th>Pozycja</th>
                    <th>Ilość</th>
                    <th className="quote-item-price">Cena</th>
                  </tr>
                </thead>
                <tbody>
                  {quote.items.map((item, index) => (
                    <tr key={index}>
                      <td data-label="Pozycja">
                        <div className="quote-item-name">{item.name}</div>
                        {item.description && <div className="quote-item-description">{item.description}</div>}
                      </td>
                      <td data-label="Ilość">{item.quantity != null ? `${item.quantity}${item.unit ? ` ${item.unit}` : ''}` : '—'}</td>
                      <td data-label="Cena" className="quote-item-price">
                        {item.totalPrice != null ? `${item.totalPrice} ${quote.currency}` : <span className="quote-item-price-unknown">wymaga wyceny</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <>
                {draftItems.map((item, index) => (
                  <div className="quote-edit-item" key={index}>
                    <div className="quote-edit-item-row">
                      <TextField label="Nazwa pozycji" value={item.name} onChange={(e) => updateDraftItem(index, { name: e.target.value })} />
                      <TextField
                        label="Opis"
                        value={item.description ?? ''}
                        onChange={(e) => updateDraftItem(index, { description: e.target.value || null })}
                      />
                    </div>
                    <div className="quote-edit-item-row quote-edit-item-row-numbers">
                      <TextField
                        label="Ilość"
                        type="number"
                        value={item.quantity ?? ''}
                        onChange={(e) => updateDraftItem(index, { quantity: e.target.value === '' ? null : Number(e.target.value) })}
                      />
                      <TextField label="Jednostka" value={item.unit ?? ''} onChange={(e) => updateDraftItem(index, { unit: e.target.value || null })} />
                      <TextField
                        label="Cena jedn."
                        type="number"
                        value={item.unitPrice ?? ''}
                        onChange={(e) => updateDraftItem(index, { unitPrice: e.target.value === '' ? null : Number(e.target.value) })}
                      />
                      <div className="quote-edit-item-total">
                        <span className="field-label">Razem</span>
                        <span>
                          {item.quantity != null && item.unitPrice != null ? `${(item.quantity * item.unitPrice).toFixed(2)} ${quote.currency}` : '—'}
                        </span>
                      </div>
                    </div>
                    <Button variant="danger" size="sm" onClick={() => removeDraftItem(index)}>
                      Usuń pozycję
                    </Button>
                  </div>
                ))}

                <Button variant="secondary" size="sm" onClick={addDraftItem}>
                  + Dodaj pozycję
                </Button>

                <div className="quote-notes-grid">
                  <TextAreaField
                    label="Notatka dla klienta"
                    hint="Zobaczy ją klient w finalnej ofercie."
                    value={draftClientNote}
                    onChange={(e) => setDraftClientNote(e.target.value)}
                  />
                  <TextAreaField
                    label="Notatka wewnętrzna"
                    hint="Widoczna tylko dla Ciebie."
                    value={draftInternalNote}
                    onChange={(e) => setDraftInternalNote(e.target.value)}
                  />
                </div>

                <div className="quote-notes-grid">
                  <TextField
                    label="Przewidywany termin realizacji"
                    hint="Opcjonalnie — pojawi się w ofercie dla klienta."
                    value={draftEstimatedTimeline}
                    onChange={(e) => setDraftEstimatedTimeline(e.target.value)}
                    placeholder="np. 2-3 tygodnie od akceptacji"
                  />
                  <TextField
                    label="Oferta ważna do"
                    hint="Opcjonalnie — data ważności oferty dla klienta."
                    type="date"
                    value={draftOfferValidUntil}
                    onChange={(e) => setDraftOfferValidUntil(e.target.value)}
                  />
                </div>
              </>
            )}

            <div className="quote-ai-vs-owner">
              <div className="quote-ai-vs-owner-row">
                <span>Wyceniono przez AI</span>
                <span>
                  {quote.aiTotal} {quote.currency}
                </span>
              </div>
              {isEdited && (
                <>
                  <div className="quote-ai-vs-owner-row quote-ai-vs-owner-current">
                    <span>Twoja wersja wyceny</span>
                    <span>
                      {isApproved ? quote.total : previewTotal(draftItems).toFixed(2)} {quote.currency}
                    </span>
                  </div>
                  <div className={`quote-ai-vs-owner-row quote-diff ${diff >= 0 ? 'quote-diff-up' : 'quote-diff-down'}`}>
                    <span>Różnica</span>
                    <span>
                      {diff >= 0 ? '+' : ''}
                      {diff.toFixed(2)} {quote.currency}
                    </span>
                  </div>
                </>
              )}
            </div>

            <div className="quote-totals-row">
              <span>Razem</span>
              <span className="quote-total-amount">
                {isApproved ? quote.total : previewTotal(draftItems).toFixed(2)} {quote.currency}
              </span>
            </div>

            {!isApproved && (
              <div className="quote-panel-actions">
                <Button variant="secondary" onClick={handleSave} loading={saving}>
                  Zapisz zmiany
                </Button>
                <Button variant="primary" className="quote-approve-button" onClick={() => setShowApproveConfirm(true)}>
                  ✅ Akceptuj wycenę
                </Button>
              </div>
            )}
          </Card>
        </div>
      </div>

      {showApproveConfirm && (
        <div className="quote-approve-overlay">
          <Card className="quote-approve-confirm-card">
            <h2>Potwierdź akceptację wyceny</h2>
            <p style={{ color: 'var(--color-text-muted)' }}>Ta wycena zostanie zablokowana do dalszej edycji. Klient NIE otrzyma jeszcze żadnej wiadomości.</p>
            <div className="quote-approve-summary">
              <div>
                <span className="field-label">Liczba pozycji</span>
                <p>{draftItems.filter((item) => item.name.trim()).length}</p>
              </div>
              <div>
                <span className="field-label">Suma</span>
                <p>
                  {previewTotal(draftItems).toFixed(2)} {quote.currency}
                </p>
              </div>
              <div>
                <span className="field-label">Klient</span>
                <p>{quote.clientName ?? '—'}</p>
              </div>
              <div>
                <span className="field-label">Zakres prac</span>
                <p>{draftItems.filter((item) => item.name.trim()).map((item) => item.name).join(', ') || '—'}</p>
              </div>
            </div>
            <div className="quote-panel-actions">
              <Button variant="primary" onClick={handleConfirmApprove} loading={approving}>
                Potwierdź akceptację
              </Button>
              <Button variant="ghost" onClick={() => setShowApproveConfirm(false)} disabled={approving}>
                Anuluj
              </Button>
            </div>
          </Card>
        </div>
      )}

      {openedPhoto && photoUrls[openedPhoto] && (
        <div className="photo-lightbox" onClick={() => setOpenedPhoto(null)}>
          <img src={photoUrls[openedPhoto]} alt="Podgląd zdjęcia" />
        </div>
      )}
    </main>
  )
}

export default QuoteDetailPage
