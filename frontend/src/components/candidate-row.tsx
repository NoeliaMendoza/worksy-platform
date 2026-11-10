import { ChevronDown } from 'lucide-react'
import { useId, useState } from 'react'
import { ConfidenceBadge } from '@/components/confidence-badge'
import { CriteriaSummary } from '@/components/criteria-summary'
import { DecisionControl } from '@/components/decision-control'
import { EvidenceStrip } from '@/components/evidence-strip'
import { ScoreMeter } from '@/components/score-meter'
import type { Criterion, Decision, RankedCandidate } from '@/features/ranking/types'
import { cn } from '@/lib/cn'

/** Nombre + primer apellido. Sin foto: se evita mostrar rasgos que sesguen la revisión. */
function initials(name: string) {
  const [first = '', surname = ''] = name.split(' ')
  return `${first.charAt(0)}${surname.charAt(0)}`
}

type CandidateRowProps = {
  candidate: RankedCandidate
  criteria: Criterion[]
  onDecide: (id: string, decision: Decision | null) => void
}

/**
 * Una tarjeta del ranking, en tres niveles de lectura:
 * quién es y cuánto encaja; un resumen de criterios; la decisión humana.
 * El detalle (la evidencia del CV) queda a un clic.
 */
export function CandidateRow({ candidate: c, criteria, onDecide }: CandidateRowProps) {
  const [open, setOpen] = useState(false)
  const detailId = useId()
  const nameId = useId()

  return (
    <article
      aria-labelledby={nameId}
      className={cn('rounded-card bg-white p-5 shadow-soft sm:p-6', c.decision === 'descartado' && 'bg-white/60')}
    >
      <div className="grid grid-cols-[auto_minmax(0,1fr)] items-start gap-x-4 sm:grid-cols-[auto_minmax(0,1fr)_auto]">
        <div className="relative">
          <span
            aria-hidden="true"
            className="grid size-11 place-items-center rounded-2xl bg-azure-100 text-sm font-semibold text-navy-700"
          >
            {initials(c.fullName)}
          </span>
          <span className="absolute -top-1.5 -left-1.5 grid size-5 place-items-center rounded-full bg-navy-900 text-[11px] font-semibold text-white tabular-nums ring-2 ring-white">
            <span className="sr-only">Puesto </span>
            {c.rank}
          </span>
        </div>

        <div className="min-w-0">
          <h3 id={nameId} className="text-base font-semibold text-ink-900">
            {c.fullName}
          </h3>
          <p className="mt-0.5 text-sm text-ink-500">
            {c.headline}, {c.location}
          </p>
        </div>

        <div className="col-span-2 mt-4 flex items-center justify-between gap-3 sm:col-span-1 sm:mt-0 sm:flex-col sm:items-end sm:gap-1.5">
          <ScoreMeter value={c.score} label={`Compatibilidad de ${c.fullName}`} />
          <ConfidenceBadge level={c.confidence} />
        </div>
      </div>

      <div className="mt-5 flex flex-col gap-4 sm:pl-15">
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
          <CriteriaSummary answers={c.answers} />
          <button
            type="button"
            aria-expanded={open}
            aria-controls={detailId}
            onClick={() => setOpen((v) => !v)}
            className="inline-flex cursor-pointer items-center gap-1 rounded-full py-1 text-sm font-semibold text-azure-600 hover:text-navy-900"
          >
            {open ? 'Ocultar evidencia' : 'Ver evidencia'}
            <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} aria-hidden="true" />
          </button>
        </div>

        <div id={detailId} hidden={!open}>
          {open && <EvidenceStrip criteria={criteria} answers={c.answers} expanded />}
        </div>

        <DecisionControl value={c.decision} candidateName={c.fullName} onChange={(d) => onDecide(c.id, d)} />
      </div>
    </article>
  )
}
