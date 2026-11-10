import type { CriterionAnswer, CriterionOutcome } from '@/features/ranking/types'
import { cn } from '@/lib/cn'

const segment: Record<CriterionOutcome, string> = {
  cumple: 'bg-high-700',
  parcial: 'bg-mid-700',
  no_cumple: 'bg-low-700',
}

const order: CriterionOutcome[] = ['cumple', 'parcial', 'no_cumple']

/** Resumen de una línea: cuántos criterios cumple. El texto lleva el dato; la barra solo lo refuerza. */
export function CriteriaSummary({ answers, className }: { answers: CriterionAnswer[]; className?: string }) {
  const count = (o: CriterionOutcome) => answers.filter((a) => a.outcome === o).length
  const met = count('cumple')
  const partial = count('parcial')
  const missing = count('no_cumple')
  const rest = [partial && `${partial} parcial${partial > 1 ? 'es' : ''}`, missing && `${missing} sin cumplir`]
    .filter(Boolean)
    .join(' y ')
  const sorted = [...answers].sort((a, b) => order.indexOf(a.outcome) - order.indexOf(b.outcome))

  return (
    <div className={cn('flex flex-wrap items-center gap-x-3 gap-y-1.5', className)}>
      <span aria-hidden="true" className="flex gap-1">
        {sorted.map((a) => (
          <span key={a.criterionId} className={cn('h-1.5 w-4 rounded-full', segment[a.outcome])} />
        ))}
      </span>
      <p className="text-sm text-ink-700">
        <span className="font-semibold text-ink-900">
          Cumple {met} de {answers.length} criterios
        </span>
        {rest && <span className="text-ink-500">, {rest}</span>}
      </p>
    </div>
  )
}
