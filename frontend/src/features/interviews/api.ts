import { apiRequest } from '@/lib/http'

export type InterviewStatus = 'INVITED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'
/** Situación calculada por la API: vencida sin respuesta, falta registrar el resultado, cerrada o vigente. */
export type InterviewPhase = 'ACTIVE' | 'EXPIRED' | 'AWAITING_OUTCOME' | 'CLOSED'
export type InterviewView = 'upcoming' | 'pending' | 'history' | 'cancelled' | 'updates'
export type InterviewChange = 'SCHEDULED' | 'UPDATED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED' | 'CHANGE_REQUESTED'
  | 'CHANGE_WITHDRAWN' | 'CHANGE_ACCEPTED' | 'CHANGE_REJECTED' | 'COMPLETED' | 'NO_SHOW'

export interface InterviewDetails {
  title: string; startsAt: string; durationMinutes: number; zoneId: string
  mode: 'ONLINE' | 'IN_PERSON' | 'VIDEO'; place: string; note: string | null
}
export interface Interview extends InterviewDetails {
  id: string; applicationId: string; version: number; status: InterviewStatus
  cancelReason?: string | null; outcomeAt?: string | null; updatedAt: string
}
export interface InterviewChangeRequest { id: string; reason: string; slots: string[]; createdAt: string }
export interface InterviewItem {
  interview: Interview; vacancyTitle: string; companyName: string
  /** Solo para la empresa. */
  candidateEmail: string | null
  organizerEmail?: string | null
  unread: boolean
  /** La persona del equipo que mira es responsable de la cita. */
  mine?: boolean
  phase?: InterviewPhase
  changeRequest?: InterviewChangeRequest | null
}
export interface InterviewPage { items: InterviewItem[]; hasMore: boolean }
export interface InterviewCounts {
  upcoming: number; pending: number; history: number; cancelled: number
  changeRequests: number; awaitingOutcome: number; expired: number
}
export interface InterviewSummary { counts: InterviewCounts; next: InterviewItem | null }
export interface InterviewContext { applicationId: string; vacancyTitle: string; candidateEmail: string }
export interface InterviewEvent {
  version: number; change: InterviewChange; startsAt: string; durationMinutes: number; zoneId: string; changedAt: string
  actor?: 'CANDIDATE' | 'COMPANY' | null; reason?: string | null; placeChanged?: boolean | null
}
export interface InterviewOverlap { interviewId: string; title: string; startsAt: string; durationMinutes: number; zoneId: string; vacancyTitle: string }

const q = (value: string) => encodeURIComponent(value)
const post = <T,>(token: string, path: string, body: unknown) => apiRequest<T>(path, { method: 'POST', token, body })

export const interviewsApi = {
  list: (token: string, view: InterviewView, page = 0, mine = false) =>
    apiRequest<InterviewPage>(`/interviews?view=${view}&page=${page}${mine ? '&mine=true' : ''}`, { token }),
  range: (token: string, from: string, to: string, mine = false) =>
    apiRequest<InterviewPage>(`/interviews?view=range&from=${q(from)}&to=${q(to)}${mine ? '&mine=true' : ''}`, { token }),
  summary: (token: string, mine = false) => apiRequest<InterviewSummary>(`/interviews/summary${mine ? '?mine=true' : ''}`, { token }),
  get: (token: string, id: string) => apiRequest<InterviewItem>(`/interviews/${id}`, { token }),
  unread: (token: string) => apiRequest<{ unread: number }>('/interviews/unread-count', { token }),
  context: (token: string, applicationId: string) => apiRequest<InterviewContext>(`/interviews/context/${applicationId}`, { token }),
  overlaps: (token: string, startsAt: string, durationMinutes: number, exclude?: string) =>
    apiRequest<InterviewOverlap[]>(`/interviews/overlaps?startsAt=${q(startsAt)}&durationMinutes=${durationMinutes}${exclude ? `&exclude=${exclude}` : ''}`, { token }),
  schedule: (token: string, id: string, applicationId: string, details: InterviewDetails) =>
    post<Interview>(token, '/interviews', { id, applicationId, details }),
  update: (token: string, id: string, expectedVersion: number, details: InterviewDetails, reason?: string) =>
    apiRequest<Interview>(`/interviews/${id}`, { method: 'PUT', token, body: { expectedVersion, details, reason: reason || null } }),
  respond: (token: string, id: string, expectedVersion: number, status: 'CONFIRMED' | 'DECLINED', reason?: string) =>
    post<Interview>(token, `/interviews/${id}/response`, { expectedVersion, status, ...(reason ? { reason } : {}) }),
  cancel: (token: string, id: string, expectedVersion: number, reason: string) =>
    post<Interview>(token, `/interviews/${id}/cancel`, { expectedVersion, reason }),
  requestChange: (token: string, id: string, expectedVersion: number, requestId: string, reason: string, slots: string[]) =>
    post<Interview>(token, `/interviews/${id}/change-requests`, { id: requestId, expectedVersion, reason, slots }),
  withdrawChange: (token: string, id: string, expectedVersion: number, requestId: string) =>
    post<Interview>(token, `/interviews/${id}/change-requests/withdraw`, { requestId, expectedVersion }),
  acceptChange: (token: string, id: string, expectedVersion: number, requestId: string, slot: number) =>
    post<Interview>(token, `/interviews/${id}/change-requests/accept`, { requestId, expectedVersion, slot }),
  rejectChange: (token: string, id: string, expectedVersion: number, requestId: string, reason?: string) =>
    post<Interview>(token, `/interviews/${id}/change-requests/reject`, { requestId, expectedVersion, reason: reason || null }),
  outcome: (token: string, id: string, expectedVersion: number, outcome: 'COMPLETED' | 'NO_SHOW') =>
    post<Interview>(token, `/interviews/${id}/outcome`, { expectedVersion, outcome }),
  read: (token: string, id: string, expectedVersion: number) =>
    post<void>(token, `/interviews/${id}/read`, { expectedVersion }),
  history: (token: string, id: string) => apiRequest<InterviewEvent[]>(`/interviews/${id}/history`, { token }),
  /** Archivo .ics de la cita (texto iCalendar). */
  calendarFile: async (token: string, id: string): Promise<Blob> => {
    let response: Response
    try {
      response = await fetch(`/api/v1/interviews/${id}/calendar`, { headers: { Authorization: `Bearer ${token}` }, credentials: 'same-origin' })
    } catch {
      throw new Error('No se pudo conectar con el servidor. Revisa tu conexión y vuelve a intentarlo.')
    }
    if (!response.ok) throw new Error('No se pudo preparar el archivo del calendario.')
    return response.blob()
  },
}

/** Descarga el .ics: Google Calendar, Outlook y Apple Calendar lo abren o importan; volver a descargarlo actualiza el evento. */
export async function downloadCalendarFile(token: string, id: string) {
  const blob = await interviewsApi.calendarFile(token, id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'entrevista-worksy.ics'
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
