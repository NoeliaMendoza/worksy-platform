import type { BadgeProps } from '@/components/ui/badge'
import type { CriterionKind, Modality, VacancyStatus } from './api'

export const modalityLabel: Record<Modality, string> = {
  PRESENCIAL: 'Presencial',
  HIBRIDO: 'Híbrido',
  REMOTO: 'Remoto',
}

export const statusLabel: Record<VacancyStatus, string> = {
  DRAFT: 'Borrador',
  PUBLISHED: 'Publicada',
  CLOSED: 'Cerrada',
}

export const statusTone: Record<VacancyStatus, NonNullable<BadgeProps['tone']>> = {
  DRAFT: 'neutral',
  PUBLISHED: 'high',
  CLOSED: 'mid',
}

export const kindLabel: Record<CriterionKind, string> = {
  YES_NO: 'Sí / No',
  CHOICE: 'Varias opciones',
}

const dateFormat = new Intl.DateTimeFormat('es', { dateStyle: 'long' })

export function formatDate(iso: string | null): string {
  return iso ? dateFormat.format(new Date(iso)) : ''
}
