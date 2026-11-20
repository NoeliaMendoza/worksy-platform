import type { ApplicationStage } from './api'

export const stageLabel: Record<ApplicationStage, string> = {
  RECEIVED: 'Recibida', REVIEW: 'En revisión', INTERVIEW: 'Entrevista', OFFER: 'Oferta',
  HIRED: 'Contratada', REJECTED: 'No continúa',
}
