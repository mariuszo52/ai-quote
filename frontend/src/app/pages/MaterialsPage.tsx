import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  createPriceListItem,
  deletePriceListItem,
  listPriceListItems,
  updatePriceListItem,
  type PriceListItemDto,
  type PriceListItemRequest,
  type PriceListItemSource,
} from '../../shared/api/priceListApi'
import { Alert, Badge, Button, Card, EmptyState, TextField } from '../../shared/ui'
import './MaterialsPage.css'

const SOURCE_LABELS: Record<PriceListItemSource, string> = {
  MANUAL: 'Dodane ręcznie',
  UPLOADED: 'Z importu cennika',
  CHAT: 'Z czatu',
}

const SOURCE_TONE: Record<PriceListItemSource, 'neutral' | 'info' | 'success'> = {
  MANUAL: 'neutral',
  UPLOADED: 'info',
  CHAT: 'success',
}

const SOURCE_FILTERS: { value: PriceListItemSource | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Wszystkie' },
  { value: 'MANUAL', label: 'Ręcznie' },
  { value: 'UPLOADED', label: 'Import' },
  { value: 'CHAT', label: 'Czat' },
]

function blankRequest(): PriceListItemRequest {
  return { name: '', category: '', price: null, unit: '' }
}

interface ItemFormProps {
  initial: PriceListItemRequest
  submitLabel: string
  saving: boolean
  onCancel: () => void
  onSubmit: (request: PriceListItemRequest) => void
}

function ItemForm({ initial, submitLabel, saving, onCancel, onSubmit }: ItemFormProps) {
  const [draft, setDraft] = useState<PriceListItemRequest>(initial)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!draft.name.trim()) return
    onSubmit({
      name: draft.name.trim(),
      category: draft.category?.trim() || null,
      price: draft.price,
      unit: draft.unit?.trim() || null,
    })
  }

  return (
    <form className="materials-form" onSubmit={handleSubmit}>
      <TextField label="Nazwa materiału" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="np. Rura PVC 50mm" required autoFocus />
      <div className="materials-form-row">
        <TextField label="Kategoria (opcjonalnie)" value={draft.category ?? ''} onChange={(e) => setDraft({ ...draft, category: e.target.value })} placeholder="np. Rury" />
        <TextField
          label="Cena (opcjonalnie)"
          type="number"
          value={draft.price ?? ''}
          onChange={(e) => setDraft({ ...draft, price: e.target.value === '' ? null : Number(e.target.value) })}
        />
        <TextField label="Jednostka (opcjonalnie)" value={draft.unit ?? ''} onChange={(e) => setDraft({ ...draft, unit: e.target.value })} placeholder="np. mb, szt" />
      </div>
      <div className="materials-form-actions">
        <Button type="submit" size="sm" loading={saving} disabled={!draft.name.trim()}>
          {submitLabel}
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={onCancel} disabled={saving}>
          Anuluj
        </Button>
      </div>
    </form>
  )
}

function MaterialsPage() {
  const [items, setItems] = useState<PriceListItemDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [sourceFilter, setSourceFilter] = useState<PriceListItemSource | 'ALL'>('ALL')
  const [adding, setAdding] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [savingItem, setSavingItem] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  function refresh() {
    return listPriceListItems()
      .then(setItems)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Nie udało się pobrać materiałów'))
  }

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase()
    return items.filter((item) => {
      const matchesSource = sourceFilter === 'ALL' || item.source === sourceFilter
      const matchesQuery = query === '' || item.name.toLowerCase().includes(query) || (item.category ?? '').toLowerCase().includes(query)
      return matchesSource && matchesQuery
    })
  }, [items, search, sourceFilter])

  async function handleCreate(request: PriceListItemRequest) {
    setSavingItem(true)
    setError(null)
    try {
      const created = await createPriceListItem(request)
      setItems((prev) => [created, ...prev])
      setAdding(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się dodać materiału')
    } finally {
      setSavingItem(false)
    }
  }

  async function handleUpdate(id: number, request: PriceListItemRequest) {
    setSavingItem(true)
    setError(null)
    try {
      const updated = await updatePriceListItem(id, request)
      setItems((prev) => prev.map((item) => (item.id === id ? updated : item)))
      setEditingId(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się zapisać zmian')
    } finally {
      setSavingItem(false)
    }
  }

  async function handleDelete(id: number) {
    setDeletingId(id)
    setError(null)
    try {
      await deletePriceListItem(id)
      setItems((prev) => prev.filter((item) => item.id !== id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nie udało się usunąć materiału')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <main className="materials-page">
      <div>
        <h1>Moje materiały</h1>
        <p className="materials-subtitle">
          Lista materiałów i urządzeń, których używasz przy realizacji zleceń (np. rury, klimatyzatory, konkretne modele) — AI korzysta z
          niej razem z cennikiem przy wycenie dla klientów. <strong>Im więcej materiałów i informacji tu dodasz, tym dokładniejsza będzie wycena.</strong>
        </p>
        <p className="materials-subtitle">
          Możesz dodawać materiały ręcznie poniżej, albo po prostu o nich wspomnieć w rozmowie z AI w zakładce{' '}
          <Link to="/app/onboarding">Wiedza firmy</Link> — jeśli danego materiału jeszcze nie ma na liście, AI doda go sam.
        </p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      <Card padded={false}>
        <div className="materials-toolbar">
          <input
            className="input materials-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Szukaj po nazwie lub kategorii..."
          />
          <div className="materials-filters">
            {SOURCE_FILTERS.map((f) => (
              <button
                key={f.value}
                type="button"
                className={`materials-filter-chip ${sourceFilter === f.value ? 'materials-filter-chip-active' : ''}`}
                onClick={() => setSourceFilter(f.value)}
              >
                {f.label}
              </button>
            ))}
          </div>
          {!adding && (
            <Button size="sm" onClick={() => setAdding(true)}>
              + Dodaj ręcznie
            </Button>
          )}
        </div>

        {adding && (
          <div className="materials-form-wrapper">
            <ItemForm initial={blankRequest()} submitLabel="Dodaj materiał" saving={savingItem} onCancel={() => setAdding(false)} onSubmit={handleCreate} />
          </div>
        )}

        {!loading && filtered.length === 0 && (
          <EmptyState
            title={items.length === 0 ? 'Brak materiałów' : 'Nic nie pasuje do wyszukiwania'}
            description={
              items.length === 0
                ? 'Dodaj materiały ręcznie poniżej, wspomnij o nich w rozmowie z AI w „Wiedza firmy”, albo prześlij tam cennik — pozycje pojawią się tutaj automatycznie.'
                : 'Spróbuj zmienić wyszukiwaną frazę albo filtr źródła.'
            }
          />
        )}

        {filtered.length > 0 && (
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Nazwa</th>
                  <th>Kategoria</th>
                  <th>Cena</th>
                  <th>Jednostka</th>
                  <th>Źródło</th>
                  <th>Akcje</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((item) =>
                  editingId === item.id ? (
                    <tr key={item.id}>
                      <td colSpan={6}>
                        <ItemForm
                          initial={{ name: item.name, category: item.category, price: item.price, unit: item.unit }}
                          submitLabel="Zapisz zmiany"
                          saving={savingItem}
                          onCancel={() => setEditingId(null)}
                          onSubmit={(request) => handleUpdate(item.id, request)}
                        />
                      </td>
                    </tr>
                  ) : (
                    <tr key={item.id}>
                      <td data-label="Nazwa">{item.name}</td>
                      <td data-label="Kategoria">{item.category ?? '—'}</td>
                      <td data-label="Cena">{item.price != null ? `${item.price} zł` : '—'}</td>
                      <td data-label="Jednostka">{item.unit ?? '—'}</td>
                      <td data-label="Źródło">
                        <Badge tone={SOURCE_TONE[item.source]}>{SOURCE_LABELS[item.source]}</Badge>
                      </td>
                      <td data-label="Akcje">
                        <div className="materials-row-actions">
                          <button type="button" className="materials-link-btn" onClick={() => setEditingId(item.id)}>
                            Edytuj
                          </button>
                          <button
                            type="button"
                            className="materials-link-btn materials-link-btn-danger"
                            onClick={() => handleDelete(item.id)}
                            disabled={deletingId === item.id}
                          >
                            {deletingId === item.id ? 'Usuwanie...' : 'Usuń'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </main>
  )
}

export default MaterialsPage
