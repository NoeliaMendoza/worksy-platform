import type { CriterionKind } from './api'

/** Criterio mientras se edita. `uid` solo sirve para React; la clave real (q1, q2…) la asigna la API. */
export interface CriterionDraft {
  uid: string
  label: string
  question: string
  kind: CriterionKind
  options: string[]
}

export type CriterionErrors = { label?: string; question?: string; options?: string }
export function criterionErrors(c: CriterionDraft): CriterionErrors {
  const errors: CriterionErrors = {}
  const length = (text: string) => [...text.trim()].length
  if (length(c.label) < 2 || length(c.label) > 60) errors.label = 'Escribe un nombre de entre 2 y 60 caracteres.'
  if (length(c.question) < 5 || length(c.question) > 2000) errors.question = 'Escribe una pregunta de entre 5 y 2000 caracteres.'
  if (c.kind === 'CHOICE') {
    const options = c.options.map((o) => o.trim().toLocaleLowerCase('en-US'))
    if (options.length < 2 || options.length > 6 || c.options.some((o) => length(o) < 1 || length(o) > 80)) {
      errors.options = 'Completa entre 2 y 6 opciones, de hasta 80 caracteres cada una.'
    } else if (new Set(options).size !== options.length) errors.options = 'Las opciones no pueden repetirse.'
  }
  return errors
}

/** Límite del servicio de inferencia (preguntas por petición). */
export const MAX_CRITERIA = 50
export const MAX_OPTIONS = 6

let counter = 0
export function newCriterion(partial: Partial<CriterionDraft> = {}): CriterionDraft {
  counter += 1
  return { uid: `c${counter}`, label: '', question: '', kind: 'YES_NO', options: ['', ''], ...partial }
}
