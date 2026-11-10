import { Check, Minus, X } from 'lucide-react'
import { outcomeLabel } from '@/features/ranking/labels'
import type { Criterion, CriterionAnswer, CriterionOutcome } from '@/features/ranking/types'
import { cn } from '@/lib/cn'

const outcomeStyle: Record<CriterionOutcome, { Icon: typeof Check; chip: string; icon: string }> = {
  cumple: { Icon: Check, chip: 'bg-high-100/60 text-ink-900', icon: 'bg-high-700 text-white' },
  parcial: { Icon: Minus, chip: 'bg-mid-100/70 text-ink-900', icon: 'bg-mid-700 text-white' },
  no_cumple: { Icon: X, chip: 'bg-low-100/70 text-ink-900', icon: 'bg-low-700 text-white' },
}

type EvidenceStripProps = {
  criteria: Criterion[]
  answers: CriterionAnswer[]
  /** Muestra la cita del CV que respalda cada respuesta. */
  expanded?: boolean
  id?: string
}

/** Resultado por criterio de la vacante, con forma (icono) y texto además del color. */
export function EvidenceStrip({ criteria, answers, expanded = false, id }: EvidenceStripProps) {
  const byId = new Map(answers.map((a) => [a.criterionId, a]))
  return (
    <ul id={id} aria-label="Criterios de la vacante" className={cn(expanded ? 'grid gap-2 sm:grid-cols-2' : 'flex flex-wrap gap-1.5')}>
      {criteria.map((c) => {
        const a = byId.get(c.id)
        if (!a) return null
        const { Icon, chip, icon } = outcomeStyle[a.outcome]
        return (
          <li
            key={c.id}
            className={cn(
              'text-xs',
              expanded ? 'rounded-2xl bg-azure-50 p-3' : cn('inline-flex items-center gap-1.5 rounded-full py-1 pr-2.5 pl-1', chip),
            )}
          >
            {expanded ? (
              <>
                <p className="flex items-center gap-2 font-semibold text-ink-900">
                  <span className={cn('grid size-4 place-items-center rounded-full', icon)}>
                    <Icon className="size-3" strokeWidth={3} aria-hidden="true" />
                  </span>
                  {c.label}: {a.answer}
                  <span className="font-medium text-ink-500">({outcomeLabel[a.outcome]})</span>
                </p>
                <p className="mt-1.5 leading-relaxed text-ink-700">
                  {a.evidence ? `“${a.evidence}”` : 'El CV no menciona este criterio.'}
                </p>
              </>
            ) : (
              <>
                <span className={cn('grid size-4 place-items-center rounded-full', icon)}>
                  <Icon className="size-3" strokeWidth={3} aria-hidden="true" />
                </span>
                <span className="font-medium">{c.label}</span>
                <span className="sr-only">: {outcomeLabel[a.outcome]}</span>
              </>
            )}
          </li>
        )
      })}
    </ul>
  )
}
