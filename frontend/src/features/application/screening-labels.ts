import type { BadgeProps } from '@/components/ui/badge'
import type { ScreeningStatus } from './api'

/** Estado de la preselección en palabras. Nunca dice "preseleccionado": eso lo decide una persona. */
export const screeningLabel: Record<ScreeningStatus, { text: string; tone: NonNullable<BadgeProps['tone']> }> = {
  PENDING: { text: 'En cola de análisis', tone: 'neutral' },
  PROCESSING: { text: 'Analizando', tone: 'azure' },
  COMPLETED: { text: 'Análisis listo', tone: 'high' },
  NEEDS_REVIEW: { text: 'Análisis con dudas', tone: 'mid' },
  FAILED: { text: 'No se pudo analizar', tone: 'low' },
}
