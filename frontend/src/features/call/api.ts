import { apiRequest } from '@/lib/http'

export type CallRole = 'CANDIDATE' | 'INTERVIEWER'
export interface CallPeer { userId: string; role: CallRole; label: string; present: boolean }
export interface CallStatus { joinable: boolean; unavailableReason: string | null; opensAt: string; closesAt: string; present: CallPeer[] }
export interface CallJoin {
  sessionId: string; interviewId: string; role: CallRole
  /** Token de sala: solo sirve para la señalización de esta sala y vence al cerrarse. */
  roomToken: string; expiresAt: string
  iceServers: string[]; peers: CallPeer[]
}

export const callApi = {
  status: (token: string, interviewId: string) => apiRequest<CallStatus>(`/interviews/${interviewId}/call`, { token }),
  join: (token: string, interviewId: string) => apiRequest<CallJoin>(`/interviews/${interviewId}/call/join`, { method: 'POST', token }),
  leave: (token: string, interviewId: string) => apiRequest<void>(`/interviews/${interviewId}/call/leave`, { method: 'POST', token }),
  end: (token: string, interviewId: string) => apiRequest<void>(`/interviews/${interviewId}/call/end`, { method: 'POST', token }),
  /** Al cerrar la pestaña: sigue enviándose aunque la página se descargue. */
  leaveOnUnload: (token: string, interviewId: string) => {
    void fetch(`/api/v1/interviews/${interviewId}/call/leave`, { method: 'POST', keepalive: true, headers: { Authorization: `Bearer ${token}` } })
      .catch(() => undefined)
  },
}
