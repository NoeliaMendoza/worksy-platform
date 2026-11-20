import { apiRequest } from '@/lib/http'

export type InterviewStatus = 'INVITED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED'
export type InterviewView = 'upcoming' | 'history' | 'updates'
export interface InterviewDetails {
  title: string; startsAt: string; durationMinutes: number; zoneId: string
  mode: 'ONLINE' | 'IN_PERSON'; place: string; note: string | null
}
export interface Interview extends InterviewDetails {
  id: string; applicationId: string; version: number; status: InterviewStatus; updatedAt: string
}
export interface InterviewItem {
  interview: Interview; vacancyTitle: string; companyName: string; candidateEmail: string; unread: boolean
}
export interface InterviewPage { items: InterviewItem[]; hasMore: boolean }
export interface InterviewContext { applicationId: string; vacancyTitle: string; candidateEmail: string }
export interface InterviewEvent { version: number; change: string; startsAt: string; durationMinutes: number; zoneId: string; changedAt: string }

export const interviewsApi = {
  list: (token: string, view: InterviewView, page = 0) => apiRequest<InterviewPage>(`/interviews?view=${view}&page=${page}`, { token }),
  unread: (token: string) => apiRequest<{ unread: number }>('/interviews/unread-count', { token }),
  context: (token: string, applicationId: string) => apiRequest<InterviewContext>(`/interviews/context/${applicationId}`, { token }),
  schedule: (token: string, id: string, applicationId: string, details: InterviewDetails) =>
    apiRequest<Interview>('/interviews', { method: 'POST', token, body: { id, applicationId, details } }),
  update: (token: string, id: string, expectedVersion: number, details: InterviewDetails) =>
    apiRequest<Interview>(`/interviews/${id}`, { method: 'PUT', token, body: { expectedVersion, details } }),
  respond: (token: string, id: string, expectedVersion: number, status: 'CONFIRMED' | 'DECLINED') =>
    apiRequest<Interview>(`/interviews/${id}/response`, { method: 'POST', token, body: { expectedVersion, status } }),
  cancel: (token: string, id: string, expectedVersion: number) =>
    apiRequest<Interview>(`/interviews/${id}/cancel`, { method: 'POST', token, body: { expectedVersion } }),
  read: (token: string, id: string, expectedVersion: number) =>
    apiRequest<void>(`/interviews/${id}/read`, { method: 'POST', token, body: { expectedVersion } }),
  history: (token: string, id: string) => apiRequest<InterviewEvent[]>(`/interviews/${id}/history`, { token }),
}
