import type { ApplicationStage, ScreeningStatus } from '@/features/application/api'
import type { VacancyStatus } from '@/features/vacancy/api'
import { apiRequest } from '@/lib/http'

/**
 * Postulación vista en "Candidatos". Si la persona no comparte sus datos (shared = false), no trae nombre,
 * correo, CV, etapa, análisis ni entrevista. Nunca trae la puntuación del modelo: se compara solo dentro del
 * ranking de cada vacante.
 */
export interface CompanyApplication {
  applicationId: string
  vacancyId: string
  vacancyTitle: string
  vacancyStatus: VacancyStatus
  appliedAt: string
  shared: boolean
  candidateName: string | null
  candidateEmail: string | null
  cvId: string | null
  stage: ApplicationStage | null
  screeningStatus: ScreeningStatus | null
  screeningFailureCode: string | null
  nextInterviewAt: string | null
}

export interface CompanyApplicationPage {
  items: CompanyApplication[]
  total: number
  page: number
  size: number
}

export type CandidateFilters = { vacancyId?: string; stage?: ApplicationStage; q?: string }

/** VALOR SUPUESTO: filas por página (la API acepta hasta 100). */
export const PAGE_SIZE = 25

export const companyApplicationsApi = {
  search: (token: string, filters: CandidateFilters, page = 0) => {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
    if (filters.vacancyId) params.set('vacancyId', filters.vacancyId)
    if (filters.stage) params.set('stage', filters.stage)
    if (filters.q?.trim()) params.set('q', filters.q.trim())
    return apiRequest<CompanyApplicationPage>(`/companies/me/applications?${params}`, { token })
  },
  find: (token: string, applicationId: string) =>
    apiRequest<CompanyApplication>(`/companies/me/applications/${applicationId}`, { token }),
}

/** Nombre para mostrar: el del perfil, si no el correo; sin permiso, nada personal. */
export function candidateLabel(a: Pick<CompanyApplication, 'shared' | 'candidateName' | 'candidateEmail'>) {
  if (!a.shared) return 'Datos no compartidos'
  return a.candidateName?.trim() || a.candidateEmail || 'Persona candidata'
}
