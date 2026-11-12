import { apiRequest } from '@/lib/http'

export interface CompanyOverview {
  id: string
  name: string
  createdAt: string
  administrators: number
  recruiters: number
  vacancies: number
  publishedVacancies: number
}

export interface CompanyPage {
  items: CompanyOverview[]
  total: number
  page: number
  size: number
}

export function listPlatformCompanies(token: string, query: string, page: number, size = 20) {
  const params = new URLSearchParams({ q: query, page: String(page), size: String(size) })
  return apiRequest<CompanyPage>(`/admin/companies?${params}`, { token })
}
