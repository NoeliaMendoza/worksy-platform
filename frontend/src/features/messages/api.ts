import { apiRequest } from '@/lib/http'

/** Una conversación en la bandeja (una por postulación). */
export interface Conversation {
  applicationId: string
  vacancyTitle: string
  companyName: string
  /** Solo para la empresa y si la persona comparte sus datos. */
  candidateEmail: string | null
  lastMessage: string
  lastSentAt: string
  lastFromCandidate: boolean
  unread: number
}

export interface Message {
  id: string
  applicationId: string
  body: string
  sentAt: string
  fromCandidate: boolean
  mine: boolean
}

/** Mensajes en orden cronológico. */
export interface MessagePage {
  messages: Message[]
  hasMore: boolean
  /** Si quien consulta puede escribir ahora (la empresa escribe primero). */
  canWrite: boolean
}

/** VALOR SUPUESTO de la API: largo máximo de un mensaje. */
export const MESSAGE_MAX = 2000

export const messagesApi = {
  inbox: (token: string) => apiRequest<Conversation[]>('/conversations', { token }),
  page: (token: string, applicationId: string, before?: string) =>
    apiRequest<MessagePage>(
      `/applications/${applicationId}/messages${before ? `?before=${encodeURIComponent(before)}` : ''}`,
      { token },
    ),
  send: (token: string, applicationId: string, body: string) =>
    apiRequest<Message>(`/applications/${applicationId}/messages`, { method: 'POST', body: { body }, token }),
  markRead: (token: string, applicationId: string) =>
    apiRequest<void>(`/applications/${applicationId}/messages/read`, { method: 'POST', token }),
}
