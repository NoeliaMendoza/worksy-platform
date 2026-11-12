/**
 * Modelo de la vista del ranking. Refleja lo que la API devolverá para una vacante;
 * el contrato definitivo se fijará en OpenAPI cuando exista el endpoint (F1/F2).
 */

/** Nivel de confianza del modelo. Siempre se muestra con palabras, no solo con color. */
export type ConfidenceLevel = 'alta' | 'media' | 'baja'

/** Decisión humana sobre un CV. `null` significa que aún nadie lo ha revisado. */
export type Decision = 'preseleccionado' | 'en_espera' | 'descartado'

/** Resultado de un criterio de la vacante según la evidencia encontrada en el CV. */
export type CriterionOutcome = 'cumple' | 'parcial' | 'no_cumple'

export interface CriterionAnswer {
  criterionId: string
  /** Respuesta corta del modelo, p. ej. "Sí" o "3 a 5 años". */
  answer: string
  outcome: CriterionOutcome
  /** Fragmento del CV que respalda la respuesta. Vacío si no hay evidencia. */
  evidence: string
  /** El proveedor devolvió una respuesta, pero no una cita del PDF. */
  evidenceUnavailable?: boolean
}

export interface Criterion {
  id: string
  /** Nombre corto para la tira de evidencia. */
  label: string
  /** Pregunta completa tal como se envía al servicio de inferencia. */
  question: string
}

export interface RankedCandidate {
  id: string
  /** Posición en el ranking completo (1 = mayor compatibilidad). */
  rank: number
  fullName: string
  headline: string
  location: string
  receivedLabel: string
  /** Compatibilidad entre el CV y la vacante, de 0 a 100. */
  score: number
  confidence: ConfidenceLevel
  answers: CriterionAnswer[]
  decision: Decision | null
}

export interface Vacancy {
  id: string
  /** Título dividido para poder resaltar una palabra sin HTML en los datos. */
  title: { lead: string; emphasis: string }
  company: string
  location: string
  modality: string
  publishedLabel: string
  criteria: Criterion[]
  modelVersion: string
  analyzedLabel: string
}

export interface VacancyRanking {
  vacancy: Vacancy
  candidates: RankedCandidate[]
}

export type DecisionFilter = 'todos' | 'sin_revisar' | Decision
