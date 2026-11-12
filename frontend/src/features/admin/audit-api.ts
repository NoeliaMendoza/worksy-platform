import type { Role } from '@/features/auth/api'
import { apiRequest } from '@/lib/http'

export const auditActionLabel = {
  COMPANY_REGISTERED: 'Empresa registrada',
  VACANCY_CREATED: 'Vacante creada',
  VACANCY_UPDATED: 'Vacante actualizada',
  VACANCY_PUBLISHED: 'Vacante publicada',
  VACANCY_CLOSED: 'Vacante cerrada',
  SCREENING_REQUESTED: 'Análisis solicitado',
  REVIEW_PRESELECTED: 'Decisión: preseleccionado',
  REVIEW_WAITLISTED: 'Decisión: en espera',
  REVIEW_REJECTED: 'Decisión: descartado',
  REVIEW_CLEARED: 'Decisión retirada',
} as const

export type AuditAction = keyof typeof auditActionLabel

export interface AuditEntry {
  id: string
  occurredAt: string
  actorId: string
  actorEmail: string
  actorRole: Role
  companyId: string | null
  companyName: string | null
  action: AuditAction
  targetId: string
  targetLabel: string
}

export interface AuditPage {
  items: AuditEntry[]
  total: number
  page: number
  size: number
}

export function listAuditEvents(token: string, query: string, action: AuditAction | '', page: number, size = 20) {
  const params = new URLSearchParams({ q: query, action, page: String(page), size: String(size) })
  return apiRequest<AuditPage>(`/admin/audit-events?${params}`, { token })
}
