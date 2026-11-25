import type { InterviewChange, InterviewEvent, InterviewItem } from './api'
import { longWhen } from './interview-time'

export type Tone = 'neutral' | 'azure' | 'high' | 'mid' | 'low'
export type Marker = 'confirmed' | 'pending' | 'change' | 'closed' | 'cancelled'

/** Estado visible de una cita para quien mira: combina el estado guardado, la situación y la propuesta abierta. */
export function interviewState(item: InterviewItem, company: boolean): { label: string; tone: Tone; marker: Marker } {
  const { interview: i, phase } = item
  if (i.status === 'CANCELLED') return { label: 'Cancelada', tone: 'low', marker: 'cancelled' }
  if (i.status === 'COMPLETED') return { label: 'Realizada', tone: 'neutral', marker: 'closed' }
  if (i.status === 'NO_SHOW') return { label: 'No asistió', tone: 'neutral', marker: 'closed' }
  if (i.status === 'DECLINED') return { label: company ? 'No puede asistir' : 'No asistirás', tone: 'neutral', marker: 'closed' }
  if (phase === 'EXPIRED') return { label: 'Sin respuesta', tone: 'mid', marker: 'closed' }
  if (phase === 'AWAITING_OUTCOME') return company ? { label: 'Falta registrar', tone: 'mid', marker: 'closed' }
    : { label: 'Confirmada', tone: 'high', marker: 'closed' }
  if (item.changeRequest) return { label: 'Cambio solicitado', tone: 'azure', marker: 'change' }
  if (i.status === 'CONFIRMED') return { label: 'Confirmada', tone: 'high', marker: 'confirmed' }
  return { label: 'Por confirmar', tone: 'mid', marker: 'pending' }
}

/** Punto de la línea de tiempo: lleno = confirmada; anillo = por confirmar o con propuesta; gris = terminada. */
export const markerClass: Record<Marker, string> = {
  confirmed: 'border-azure-600 bg-azure-600',
  pending: 'border-mid-700 bg-white',
  change: 'border-azure-600 bg-white',
  closed: 'border-ink-300 bg-ink-300',
  cancelled: 'border-ink-300 bg-white',
}

const verbs: Record<InterviewChange, string> = {
  SCHEDULED: 'agendó la entrevista',
  UPDATED: 'cambió los detalles',
  CONFIRMED: 'confirmó la asistencia',
  DECLINED: 'avisó que no puede asistir',
  CANCELLED: 'canceló la entrevista',
  CHANGE_REQUESTED: 'propuso otra hora',
  CHANGE_WITHDRAWN: 'retiró su propuesta de otra hora',
  CHANGE_ACCEPTED: 'aceptó una de las horas propuestas',
  CHANGE_REJECTED: 'mantuvo la hora actual',
  COMPLETED: 'registró la entrevista como realizada',
  NO_SHOW: 'registró que la persona no asistió',
}

/** Frase del historial desde el punto de vista de quien mira, sin identificar personas. */
export function historyLine(event: InterviewEvent, previous: InterviewEvent | undefined, company: boolean): string {
  const who = event.actor === 'CANDIDATE' ? (company ? 'La persona candidata' : 'Tú') : event.actor === 'COMPANY' ? (company ? 'Tu equipo' : 'La empresa') : 'Se'
  let verb = verbs[event.change]
  if (who === 'Tú') verb = verb.replace('confirmó', 'confirmaste').replace('avisó', 'avisaste').replace('propuso', 'propusiste').replace('retiró su', 'retiraste tu')
  if ((event.change === 'UPDATED' || event.change === 'CHANGE_ACCEPTED') && previous && previous.startsAt !== event.startsAt) {
    verb = `movió la cita al ${longWhen(event.startsAt, event.durationMinutes).replace(/^./, (c) => c.toLowerCase())}`
  } else if (event.change === 'UPDATED' && event.placeChanged) {
    verb = 'cambió el lugar o la modalidad'
  }
  return `${who} ${verb}`
}
