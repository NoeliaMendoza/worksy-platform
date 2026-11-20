import { apiRequest } from '@/lib/http'

export type Modality = 'PRESENCIAL' | 'HIBRIDO' | 'REMOTO'
export type VacancyStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type CriterionKind = 'YES_NO' | 'CHOICE'

export interface OfferDetails {
  contractType: 'INDEFINITE' | 'FIXED_TERM' | 'SERVICES' | 'INTERNSHIP' | 'OTHER' | null
  workSchedule: 'FULL_TIME' | 'PART_TIME' | 'FLEXIBLE' | null
  area: string | null
  experienceLevel: string | null
  responsibilities: string | null
  requirements: string | null
  desirable: string | null
  benefits: string | null
  schedule: string | null
  remoteConditions: string | null
  salaryMin: number | null
  salaryMax: number | null
  currency: string | null
  salaryPeriod: 'HOUR' | 'MONTH' | 'YEAR' | null
}

export interface Criterion {
  key: string
  label: string
  question: string
  kind: CriterionKind
  options: string[]
}

/** Lo que la empresa edita. Las claves de los criterios las asigna la API. */
export interface VacancyInput {
  offer?: OfferDetails | null
  title: string
  description: string
  location: string
  modality: Modality
  criteria: { label: string; question: string; kind: CriterionKind; options?: string[] }[]
}

export interface CompanyVacancy {
  offer?: OfferDetails | null
  id: string
  title: string
  description: string
  location: string
  modality: Modality
  status: VacancyStatus
  criteria: Criterion[]
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  closedAt: string | null
}

export interface PublicVacancySummary {
  id: string
  title: string
  companyName: string
  location: string
  modality: Modality
  publishedAt: string
}

export interface PublicVacancy extends PublicVacancySummary {
  offer?: OfferDetails | null
  description: string
  criteria: Criterion[]
}

const base = '/companies/me/vacancies'

export const companyVacancies = {
  list: (token: string) => apiRequest<CompanyVacancy[]>(base, { token }),
  get: (token: string, id: string) => apiRequest<CompanyVacancy>(`${base}/${id}`, { token }),
  create: (token: string, body: VacancyInput) => apiRequest<CompanyVacancy>(base, { method: 'POST', body, token }),
  update: (token: string, id: string, body: VacancyInput) =>
    apiRequest<CompanyVacancy>(`${base}/${id}`, { method: 'PUT', body, token }),
  publish: (token: string, id: string) => apiRequest<CompanyVacancy>(`${base}/${id}/publish`, { method: 'POST', token }),
  close: (token: string, id: string) => apiRequest<CompanyVacancy>(`${base}/${id}/close`, { method: 'POST', token }),
}

/** Vacantes publicadas: no necesitan sesión. */
export const publicVacancies = {
  list: () => apiRequest<PublicVacancySummary[]>('/vacancies'),
  get: (id: string) => apiRequest<PublicVacancy>(`/vacancies/${id}`),
}
