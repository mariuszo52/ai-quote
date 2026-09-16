import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getMyCompany, getMyLogoUrl, removeLogo, updateBranding, uploadLogo, type CompanyResponse } from '../../shared/api/companyApi'
import { Alert, Button, Card, TextAreaField, TextField } from '../../shared/ui'
import './BrandingSettingsPage.css'

function BrandingSettingsPage() {
  const [company, setCompany] = useState<CompanyResponse | null>(null)
  const [logoUrl, setLogoUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [uploadingLogo, setUploadingLogo] = useState(false)
  const [removingLogo, setRemovingLogo] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [displayName, setDisplayName] = useState('')
  const [primaryColor, setPrimaryColor] = useState('#4f46e5')
  const [welcomeText, setWelcomeText] = useState('')
  const [contactEmail, setContactEmail] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [address, setAddress] = useState('')

  function applyCompany(data: CompanyResponse) {
    setCompany(data)
    setDisplayName(data.displayName)
    setPrimaryColor(data.primaryColor)
    setWelcomeText(data.welcomeText)
    setContactEmail(data.contactEmail ?? '')
    setContactPhone(data.contactPhone ?? '')
    setAddress(data.address ?? '')
  }

  useEffect(() => {
    getMyCompany()
      .then(applyCompany)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać ustawień firmy'))
  }, [])

  useEffect(() => {
    if (!company?.hasLogo) return
    let cancelled = false
    let url: string | null = null
    getMyLogoUrl()
      .then((fetchedUrl) => {
        if (cancelled) {
          URL.revokeObjectURL(fetchedUrl)
          return
        }
        url = fetchedUrl
        setLogoUrl(fetchedUrl)
      })
      .catch(() => {
        /* preview is a bonus — the rest of the page still works without it */
      })
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [company?.hasLogo])

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      const updated = await updateBranding({
        displayName: displayName.trim() || null,
        primaryColor: primaryColor.trim() || null,
        welcomeText: welcomeText.trim() || null,
        contactEmail: contactEmail.trim() || null,
        contactPhone: contactPhone.trim() || null,
        address: address.trim() || null,
      })
      applyCompany(updated)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zapisać ustawień')
    } finally {
      setSaving(false)
    }
  }

  async function handleLogoUpload(event: FormEvent) {
    event.preventDefault()
    const file = fileInputRef.current?.files?.[0]
    if (!file) return

    setUploadingLogo(true)
    setError(null)
    try {
      const updated = await uploadLogo(file)
      applyCompany(updated)
      if (fileInputRef.current) fileInputRef.current.value = ''
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się przesłać logo')
    } finally {
      setUploadingLogo(false)
    }
  }

  async function handleLogoRemove() {
    setRemovingLogo(true)
    setError(null)
    try {
      const updated = await removeLogo()
      applyCompany(updated)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się usunąć logo')
    } finally {
      setRemovingLogo(false)
    }
  }

  if (!company) {
    return (
      <main className="branding-page">
        {error && <Alert tone="error">{error}</Alert>}
      </main>
    )
  }

  return (
    <main className="branding-page">
      <div>
        <h1>Wygląd firmy</h1>
        <p className="branding-subtitle">Tak Twoja firma pojawi się dla klientów w publicznym czacie AI oraz na wygenerowanych ofertach PDF.</p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      <Card>
        <h2>Logo</h2>
        <div className="branding-logo-row">
          <div className="branding-logo-preview">
            {company.hasLogo && logoUrl ? (
              <img src={logoUrl} alt="Logo firmy" />
            ) : (
              <span className="branding-logo-placeholder">Brak logo</span>
            )}
          </div>
          <div className="branding-logo-actions">
            <form onSubmit={handleLogoUpload} className="branding-upload-row">
              <input className="input" type="file" ref={fileInputRef} accept="image/jpeg,image/png,image/webp" />
              <Button type="submit" size="sm" loading={uploadingLogo}>
                {company.hasLogo ? 'Zamień logo' : 'Prześlij logo'}
              </Button>
            </form>
            {company.hasLogo && (
              <Button variant="danger" size="sm" onClick={handleLogoRemove} loading={removingLogo}>
                Usuń logo
              </Button>
            )}
            <p className="branding-hint">JPEG, PNG lub WebP, maks. 2 MB.</p>
          </div>
        </div>
      </Card>

      <Card>
        <h2>Dane i kolor firmy</h2>
        <div className="branding-form-grid">
          <TextField label="Nazwa wyświetlana klientom" value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder={company.name} />
          <div>
            <label className="field-label" htmlFor="branding-color">
              Kolor przewodni
            </label>
            <div className="branding-color-row">
              <input
                id="branding-color"
                type="color"
                className="branding-color-input"
                value={primaryColor}
                onChange={(e) => setPrimaryColor(e.target.value)}
              />
              <TextField value={primaryColor} onChange={(e) => setPrimaryColor(e.target.value)} />
            </div>
          </div>
        </div>

        <TextAreaField
          label="Tekst powitalny dla klienta"
          hint="Widoczny na początku rozmowy w publicznym czacie AI."
          value={welcomeText}
          onChange={(e) => setWelcomeText(e.target.value)}
        />

        <div className="branding-form-grid">
          <TextField label="Email kontaktowy" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} />
          <TextField label="Telefon kontaktowy" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} />
        </div>
        <TextAreaField label="Adres firmy (opcjonalnie)" value={address} onChange={(e) => setAddress(e.target.value)} />

        <div className="branding-actions">
          <Button variant="primary" onClick={handleSave} loading={saving}>
            Zapisz
          </Button>
          {saved && <span className="branding-saved-status">Zapisano ✓</span>}
        </div>
      </Card>
    </main>
  )
}

export default BrandingSettingsPage
