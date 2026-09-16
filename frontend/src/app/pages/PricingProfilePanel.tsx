import { useState } from 'react'
import {
  updateProfile,
  type PricingModel,
  type PricingServiceEntry,
  type ProfileResponse,
} from '../../shared/api/onboardingApi'
import { Alert, Badge, Button, Card, EmptyState, TextAreaField, TextField } from '../../shared/ui'
import './PricingProfilePanel.css'

const PRICING_MODEL_LABELS: Record<PricingModel, string> = {
  HOURLY: 'Stawka godzinowa',
  PER_UNIT: 'Cena za jednostkę',
  PER_PROJECT: 'Cena za projekt',
  INDIVIDUAL: 'Wycena indywidualna',
}

const PRICING_MODEL_OPTIONS: PricingModel[] = ['HOURLY', 'PER_UNIT', 'PER_PROJECT', 'INDIVIDUAL']

interface DraftService extends PricingServiceEntry {
  factorsText: string
}

function toDraft(service: PricingServiceEntry): DraftService {
  return { ...service, factorsText: (service.factors ?? []).join(', ') }
}

function blankDraft(): DraftService {
  return {
    name: '',
    pricingModel: null,
    unit: null,
    basePrice: null,
    minPrice: null,
    maxPrice: null,
    minimumCharge: null,
    factors: null,
    requiresIndividualQuote: false,
    notes: null,
    factorsText: '',
  }
}

function fromDraft(draft: DraftService): PricingServiceEntry {
  const { factorsText, ...rest } = draft
  const factors = factorsText
    .split(',')
    .map((f) => f.trim())
    .filter(Boolean)
  return { ...rest, factors }
}

function formatMoney(value: number | null, unit: string | null) {
  if (value == null) return null
  return unit ? `${value} zł / ${unit}` : `${value} zł`
}

function formatRange(min: number | null, max: number | null, unit: string | null) {
  if (min == null && max == null) return null
  const range = min != null && max != null ? `${min}–${max}` : `${min ?? max}`
  return unit ? `${range} zł / ${unit}` : `${range} zł`
}

interface PricingProfilePanelProps {
  profile: ProfileResponse | null
  onSaved: (updated: ProfileResponse) => void
}

function PricingProfilePanel({ profile, onSaved }: PricingProfilePanelProps) {
  const [editing, setEditing] = useState(false)
  const [draftServices, setDraftServices] = useState<DraftService[]>([])
  const [draftNotes, setDraftNotes] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [justSaved, setJustSaved] = useState(false)

  function startEditing() {
    setDraftServices((profile?.services ?? []).map(toDraft))
    setDraftNotes(profile?.generalNotes ?? '')
    setError(null)
    setEditing(true)
  }

  function cancelEditing() {
    setEditing(false)
    setError(null)
  }

  function updateDraft(index: number, patch: Partial<DraftService>) {
    setDraftServices((prev) => prev.map((service, i) => (i === index ? { ...service, ...patch } : service)))
  }

  function removeDraft(index: number) {
    setDraftServices((prev) => prev.filter((_, i) => i !== index))
  }

  function addDraft() {
    setDraftServices((prev) => [...prev, blankDraft()])
  }

  async function handleSave() {
    const services = draftServices.map(fromDraft)
    if (services.some((s) => !s.name.trim())) {
      setError('Każda usługa musi mieć nazwę.')
      return
    }

    setSaving(true)
    setError(null)
    try {
      const updated = await updateProfile({ services, generalNotes: draftNotes.trim() || null })
      onSaved(updated)
      setEditing(false)
      setJustSaved(true)
      setTimeout(() => setJustSaved(false), 2500)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zapisać profilu')
    } finally {
      setSaving(false)
    }
  }

  const services = profile?.services ?? []
  const isEmpty = services.length === 0 && !profile?.generalNotes

  return (
    <Card>
      <div className="pricing-panel-header">
        <h2>Wiedza cenowa</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          {justSaved && <span className="onboarding-save-status">Zapisano ✓</span>}
          {!editing && (
            <Button variant="secondary" size="sm" onClick={startEditing}>
              Edytuj
            </Button>
          )}
        </div>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {!editing && isEmpty && (
        <EmptyState
          title="Brak zapisanej wiedzy cenowej"
          description="Porozmawiaj z AI po lewej stronie albo prześlij cennik — informacje pojawią się tutaj automatycznie. Możesz je też wpisać ręcznie."
        />
      )}

      {!editing && !isEmpty && (
        <>
          {services.map((service, index) => (
            <div className="pricing-service-view" key={index}>
              <div className="pricing-service-view-header">
                <h3>{service.name}</h3>
                {service.requiresIndividualQuote && <Badge tone="info">Wycena indywidualna</Badge>}
              </div>
              <div className="pricing-service-facts">
                {service.pricingModel && <span className="pricing-service-fact">{PRICING_MODEL_LABELS[service.pricingModel]}</span>}
                {formatMoney(service.basePrice, service.unit) && (
                  <span className="pricing-service-fact">cena bazowa: {formatMoney(service.basePrice, service.unit)}</span>
                )}
                {formatRange(service.minPrice, service.maxPrice, service.unit) && (
                  <span className="pricing-service-fact">widełki: {formatRange(service.minPrice, service.maxPrice, service.unit)}</span>
                )}
                {service.minimumCharge != null && <span className="pricing-service-fact">minimalna opłata: {service.minimumCharge} zł</span>}
              </div>
              {service.factors && service.factors.length > 0 && (
                <div className="pricing-service-factors">
                  {service.factors.map((factor) => (
                    <Badge key={factor} tone="neutral">
                      {factor}
                    </Badge>
                  ))}
                </div>
              )}
              {service.notes && <p className="pricing-service-notes">{service.notes}</p>}
            </div>
          ))}
          {profile?.generalNotes && (
            <div className="pricing-general-notes">
              <strong>Dodatkowe uwagi:</strong> {profile.generalNotes}
            </div>
          )}
        </>
      )}

      {editing && (
        <>
          {draftServices.map((service, index) => (
            <div className="pricing-service-edit" key={index}>
              <TextField
                label="Nazwa usługi"
                value={service.name}
                onChange={(e) => updateDraft(index, { name: e.target.value })}
                required
              />
              <div className="pricing-service-edit-row">
                <label className="field">
                  <span className="field-label">Sposób wyceny</span>
                  <select
                    className="select"
                    value={service.pricingModel ?? ''}
                    onChange={(e) => updateDraft(index, { pricingModel: (e.target.value || null) as PricingModel | null })}
                  >
                    <option value="">Nieokreślony</option>
                    {PRICING_MODEL_OPTIONS.map((option) => (
                      <option key={option} value={option}>
                        {PRICING_MODEL_LABELS[option]}
                      </option>
                    ))}
                  </select>
                </label>
                <TextField
                  label="Jednostka"
                  placeholder="np. godzina, m2, sztuka"
                  value={service.unit ?? ''}
                  onChange={(e) => updateDraft(index, { unit: e.target.value || null })}
                />
              </div>
              <div className="pricing-service-edit-row">
                <TextField
                  label="Cena bazowa (zł)"
                  type="number"
                  value={service.basePrice ?? ''}
                  onChange={(e) => updateDraft(index, { basePrice: e.target.value === '' ? null : Number(e.target.value) })}
                />
                <TextField
                  label="Minimalna opłata (zł)"
                  type="number"
                  value={service.minimumCharge ?? ''}
                  onChange={(e) => updateDraft(index, { minimumCharge: e.target.value === '' ? null : Number(e.target.value) })}
                />
              </div>
              <div className="pricing-service-edit-row">
                <TextField
                  label="Widełki od (zł)"
                  type="number"
                  value={service.minPrice ?? ''}
                  onChange={(e) => updateDraft(index, { minPrice: e.target.value === '' ? null : Number(e.target.value) })}
                />
                <TextField
                  label="Widełki do (zł)"
                  type="number"
                  value={service.maxPrice ?? ''}
                  onChange={(e) => updateDraft(index, { maxPrice: e.target.value === '' ? null : Number(e.target.value) })}
                />
              </div>
              <TextField
                label="Czynniki wpływające na cenę (oddzielone przecinkami)"
                value={service.factorsText}
                onChange={(e) => updateDraft(index, { factorsText: e.target.value })}
                placeholder="np. odległość, trudność dostępu, pilność"
              />
              <TextAreaField
                label="Uwagi do tej usługi"
                value={service.notes ?? ''}
                onChange={(e) => updateDraft(index, { notes: e.target.value || null })}
              />
              <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', fontSize: 'var(--text-sm)' }}>
                <input
                  type="checkbox"
                  checked={service.requiresIndividualQuote ?? false}
                  onChange={(e) => updateDraft(index, { requiresIndividualQuote: e.target.checked })}
                />
                Ta usługa zwykle wymaga indywidualnej wyceny
              </label>
              <Button variant="danger" size="sm" className="pricing-service-edit-remove" onClick={() => removeDraft(index)}>
                Usuń usługę
              </Button>
            </div>
          ))}

          <Button variant="secondary" size="sm" onClick={addDraft}>
            + Dodaj usługę
          </Button>

          <div style={{ marginTop: 'var(--space-3)' }}>
            <TextAreaField
              label="Dodatkowe uwagi (niezwiązane z jedną usługą)"
              value={draftNotes}
              onChange={(e) => setDraftNotes(e.target.value)}
            />
          </div>

          <div className="pricing-panel-actions">
            <Button onClick={handleSave} loading={saving}>
              Zapisz
            </Button>
            <Button variant="ghost" onClick={cancelEditing} disabled={saving}>
              Anuluj
            </Button>
          </div>
        </>
      )}

      {profile?.manuallyEditedAt && (
        <p className="pricing-panel-meta">Ostatnio poprawione ręcznie: {new Date(profile.manuallyEditedAt).toLocaleString()}</p>
      )}
    </Card>
  )
}

export default PricingProfilePanel
