import type { ConfidenceLevel, CriterionOutcome, Decision, DecisionFilter } from './types'

/** Textos visibles. Centralizados para que una acción se llame igual en todo el flujo. */

export const confidenceLabel: Record<ConfidenceLevel, string> = {
  alta: 'Confianza alta',
  media: 'Confianza media',
  baja: 'Confianza baja',
}

export const outcomeLabel: Record<CriterionOutcome, string> = {
  cumple: 'Cumple',
  parcial: 'Parcial',
  no_cumple: 'No cumple',
}

/** Verbo del botón (lo que hace la persona). */
export const decisionAction: Record<Decision, string> = {
  preseleccionado: 'Preseleccionar',
  en_espera: 'En espera',
  descartado: 'Descartar',
}

/** Estado resultante (lo que se anuncia y se filtra). */
export const decisionState: Record<Decision, string> = {
  preseleccionado: 'Preseleccionado',
  en_espera: 'En espera',
  descartado: 'Descartado',
}

export const filterLabel: Record<DecisionFilter, string> = {
  todos: 'Todos',
  sin_revisar: 'Sin revisar',
  preseleccionado: 'Preseleccionados',
  en_espera: 'En espera',
  descartado: 'Descartados',
}
