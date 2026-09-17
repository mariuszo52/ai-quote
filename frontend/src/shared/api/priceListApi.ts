import { apiFetch } from './client'

export type PriceListItemSource = 'MANUAL' | 'UPLOADED' | 'CHAT'

export interface PriceListItemDto {
  id: number
  name: string
  category: string | null
  price: number | null
  unit: string | null
  source: PriceListItemSource
  createdAt: string
  updatedAt: string
}

export interface PriceListItemRequest {
  name: string
  category: string | null
  price: number | null
  unit: string | null
}

export function listPriceListItems(): Promise<PriceListItemDto[]> {
  return apiFetch('/api/price-list-items')
}

export function createPriceListItem(request: PriceListItemRequest): Promise<PriceListItemDto> {
  return apiFetch('/api/price-list-items', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updatePriceListItem(id: number, request: PriceListItemRequest): Promise<PriceListItemDto> {
  return apiFetch(`/api/price-list-items/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deletePriceListItem(id: number): Promise<void> {
  return apiFetch(`/api/price-list-items/${id}`, { method: 'DELETE' })
}
