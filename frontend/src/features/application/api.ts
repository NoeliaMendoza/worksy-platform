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
