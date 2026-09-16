import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getPublicOffer, publicOfferPdfUrl, type PublicOfferResponse } from '../../shared/api/offerApi'
import { Card, EmptyState, Spinner } from '../../shared/ui'
import './PublicOfferPage.css'

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function PublicOfferPage() {
  const { token } = useParams<{ token: string }>()
  const [offer, setOffer] = useState<PublicOfferResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!token) return
    getPublicOffer(token)
      .then(setOffer)
      .catch(() => setError('Nie znaleziono oferty. Sprawdź, czy link jest poprawny.'))
      .finally(() => setLoading(false))
  }, [token])

  if (loading) {
    return (
      <main className="public-offer-page" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <Spinner size="lg" />
      </main>
    )
  }

  if (error || !offer || !token) {
    return (
      <main className="public-offer-page">
        <Card>
          <EmptyState title="Oferta niedostępna" description={error ?? 'Nie znaleziono oferty.'} />
        </Card>
      </main>
    )
  }

  return (
    <main className="public-offer-page">
      <Card className="public-offer-card">
        <div className="public-offer-header">
          <h1>{offer.companyName}</h1>
          <span className="public-offer-number">Oferta nr {offer.offerNumber}</span>
        </div>
        <p className="public-offer-date">Data: {formatDate(offer.createdAt)}</p>

        <div className="public-offer-section">
          <h2>Dla</h2>
          <p>{offer.clientName}</p>
        </div>

        {offer.jobDescription && (
          <div className="public-offer-section">
            <h2>Opis zlecenia</h2>
            <p>{offer.jobDescription}</p>
          </div>
        )}

        <div className="public-offer-section">
          <h2>Zakres prac i wycena</h2>
          <table className="data-table public-offer-items-table">
            <thead>
              <tr>
                <th>Pozycja</th>
                <th>Ilość</th>
                <th className="public-offer-price">Cena</th>
              </tr>
            </thead>
            <tbody>
              {offer.items.map((item, index) => (
                <tr key={index}>
                  <td data-label="Pozycja">
                    <div className="public-offer-item-name">{item.name}</div>
                    {item.description && <div className="public-offer-item-description">{item.description}</div>}
                  </td>
                  <td data-label="Ilość">{item.quantity != null ? `${item.quantity}${item.unit ? ` ${item.unit}` : ''}` : '—'}</td>
                  <td data-label="Cena" className="public-offer-price">
                    {item.totalPrice != null ? `${item.totalPrice} ${offer.currency}` : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="public-offer-total-row">
            <span>Razem</span>
            <span className="public-offer-total-amount">
              {offer.total} {offer.currency}
            </span>
          </div>
        </div>

        {(offer.estimatedTimeline || offer.validUntil) && (
          <div className="public-offer-section">
            <h2>Szczegóły oferty</h2>
            {offer.estimatedTimeline && <p>Przewidywany termin realizacji: {offer.estimatedTimeline}</p>}
            {offer.validUntil && <p>Oferta ważna do: {formatDate(offer.validUntil)}</p>}
          </div>
        )}

        {offer.companyContactEmail && (
          <div className="public-offer-section">
            <h2>Kontakt</h2>
            <p>{offer.companyContactEmail}</p>
          </div>
        )}

        <a className="btn btn-primary" href={publicOfferPdfUrl(token)} target="_blank" rel="noreferrer">
          Pobierz PDF
        </a>
      </Card>
    </main>
  )
}

export default PublicOfferPage
