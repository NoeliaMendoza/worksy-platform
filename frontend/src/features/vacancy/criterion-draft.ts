import type { CriterionKind } from './api'

/** Criterio mientras se edita. `uid` solo sirve para React; la clave real (q1, q2…) la asigna la API. */
export interface CriterionDraft {
  uid: string
  label: string
  question: string
  kind: CriterionKind
  options: string[]
}

/** Límite del servicio de inferencia (preguntas por petición). */
export const MAX_CRITERIA = 50
export const MAX_OPTIONS = 6

let counter = 0
export function newCriterion(partial: Partial<CriterionDraft> = {}): CriterionDraft {
  counter += 1
  return { uid: `c${counter}`, label: '', question: '', kind: 'YES_NO', options: ['', ''], ...partial }
}
