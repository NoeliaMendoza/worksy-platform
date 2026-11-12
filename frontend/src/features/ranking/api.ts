import type { ScreeningStatus } from '@/features/application/api'
import { apiRequest } from '@/lib/http'
import type { Decision } from './types'

export interface RankingResult {
  applicationId: string
  fullName: string | null
  email: string
  headline: string | null
  location: string | null
  appliedAt: string
  status: ScreeningStatus
  modelVersion: string | null
  overallScore: number
  confidence: number
  answers: { criterionId: string; answer: string | null; score: number | null; probability: number | null }[]
  decision: Decision | null
}

export interface RankingResponse {
  candidates: RankingResult[]
  received: number
  pending: number
  failed: number
  notRequested: number
}

export const rankingApi = {
  get: (token: string, vacancyId: string) =>
    apiRequest<RankingResponse>(`/companies/me/vacancies/${vacancyId}/ranking`, { token }),
  decide: (token: string, vacancyId: string, applicationId: string, decision: Decision | null) =>
    apiRequest<void>(`/companies/me/vacancies/${vacancyId}/ranking/${applicationId}/decision`, {
      method: 'PUT', token, body: { decision },
    }),
}
