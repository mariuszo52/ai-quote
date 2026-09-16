import { apiFetch } from './client'

export interface KnowledgeSourceDto {
  id: number
  type: string
  status: string
  originalFilename: string
  extractedSummary: string | null
  createdAt: string
}

export function uploadSource(file: File): Promise<KnowledgeSourceDto> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch('/api/onboarding/sources', { method: 'POST', body: formData })
}

export function listSources(): Promise<KnowledgeSourceDto[]> {
  return apiFetch('/api/onboarding/sources')
}
