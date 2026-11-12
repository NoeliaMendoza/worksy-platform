import { apiRequest } from '@/lib/http'
import type { VacancyStatus } from '@/features/vacancy/api'

/** Postulación vista por la persona que se postuló. */
export interface MyApplication {
  id: string
  vacancyId: string
  vacancyTitle: string
  companyName: string
  vacancyStatus: VacancyStatus
  cvId: string
  appliedAt: string
}

/** Postulación vista por la empresa. Sin permiso de la persona, no trae su correo ni su CV. */
export interface ReceivedApplication {
  id: string
  appliedAt: string
  shared: boolean
  candidateEmail: string | null
  cvId: string | null
}

export type ScreeningStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'NEEDS_REVIEW' | 'FAILED'

export interface ScreeningRequest {
  id: string
  applicationId: string
  provider: string
  status: ScreeningStatus
  modelVersion: string | null
  overallScore: number | null
  confidence: number | null
  requestedAt: string
  updatedAt: string
}

export interface ScreeningBatch {
  requests: ScreeningRequest[]
  skipped: number
  replayed: boolean
}

export const screeningApi = {
  /**
   * Pide la preselección de todas las postulaciones de la vacante. `idempotencyKey` identifica la
   * intención: reintentar con la misma clave no duplica el trabajo.
   */
  request: (token: string, vacancyId: string, idempotencyKey: string) =>
    apiRequest<ScreeningBatch>(`/companies/me/vacancies/${vacancyId}/screenings`, {
      method: 'POST',
      body: {},
      token,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  list: (token: string, vacancyId: string) =>
    apiRequest<ScreeningRequest[]>(`/companies/me/vacancies/${vacancyId}/screenings`, { token }),
  retry: (token: string, vacancyId: string, requestId: string, idempotencyKey: string) =>
    apiRequest<{ request: ScreeningRequest; replayed: boolean }>(
      `/companies/me/vacancies/${vacancyId}/screenings/${requestId}/retry`, {
        method: 'POST', token, headers: { 'Idempotency-Key': idempotencyKey },
      },
    ),
}

export const applicationsApi = {
  apply: (token: string, vacancyId: string, cvId: string) =>
    apiRequest<{ id: string }>(`/vacancies/${vacancyId}/applications`, { method: 'POST', body: { cvId }, token }),
  mine: (token: string) => apiRequest<MyApplication[]>('/candidates/me/applications', { token }),
  withdraw: (token: string, id: string) =>
    apiRequest<void>(`/candidates/me/applications/${id}`, { method: 'DELETE', token }),
  forVacancy: (token: string, vacancyId: string) =>
    apiRequest<ReceivedApplication[]>(`/companies/me/vacancies/${vacancyId}/applications`, { token }),
  /** Enlace temporal (5 minutos) al PDF. Solo si la persona mantiene el permiso de compartirlo. */
  cvLink: (token: string, vacancyId: string, applicationId: string) =>
    apiRequest<{ url: string; expiresAt: string }>(
      `/companies/me/vacancies/${vacancyId}/applications/${applicationId}/cv`,
      { token },
    ),
}
