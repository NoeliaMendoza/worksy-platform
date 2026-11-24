import type { CriterionDraft } from './criterion-draft'

/** Ejemplos explícitos: se añaden solo por acción de la empresa y pueden editarse. */
export const criterionTemplates: { id: string; name: string; description: string; value: Omit<CriterionDraft, 'uid'> }[] = [
  { id: 'experience', name: 'Experiencia con una tecnología', description: 'Comprueba si el CV declara experiencia. Cambia Java por la tecnología del puesto.',
    value: { label: 'Experiencia con Java', question: '¿El CV declara experiencia profesional con Java?', kind: 'YES_NO', options: ['', ''] } },
  { id: 'years', name: 'Tiempo de experiencia declarado', description: 'Usa rangos sin solapamiento y contempla que el CV no indique la duración.',
    value: { label: 'Tiempo de experiencia con Java', question: '¿Qué duración de experiencia profesional con Java declara el CV?', kind: 'CHOICE', options: ['3 años o más', 'De 1 a menos de 3 años', 'Menos de 1 año', 'No se indica en el CV'] } },
  { id: 'language', name: 'Nivel de idioma declarado', description: 'Evalúa el nivel escrito en el CV; no sustituye una prueba de idioma.',
    value: { label: 'Nivel de inglés declarado', question: '¿Qué nivel de inglés declara explícitamente el CV?', kind: 'CHOICE', options: ['Avanzado (C1–C2)', 'Intermedio (B1–B2)', 'Básico (A1–A2)', 'No se indica en el CV'] } },
]

export function optionScore(index: number, total: number): number {
  return total > 1 ? Math.round((1 - index / (total - 1)) * 100) : 0
}
