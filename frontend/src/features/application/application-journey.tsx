import { Check, CircleDot, X } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { MyApplication, ApplicationStage } from './api'
import { stageLabel } from './stage-labels'

const route: ApplicationStage[] = ['RECEIVED', 'REVIEW', 'INTERVIEW', 'OFFER', 'HIRED']
const date = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short', year: 'numeric' })

/** El recorrido muestra registros, no porcentajes ni etapas completadas por inferencia. */
export function ApplicationJourney({ application }: { application: MyApplication }) {
  const current = application.stage ?? 'RECEIVED'
  const events = application.milestones ?? []
  const reached = new Map(events.map((event) => [event.stage, event.changedAt]))
  // La fecha de postulación prueba la recepción incluso con una API anterior.
  if (!reached.has('RECEIVED')) reached.set('RECEIVED', application.appliedAt)
  const lastReached = Math.max(0, ...route.map((stage, index) => reached.has(stage) ? index : -1))
  const stages: ApplicationStage[] = current === 'REJECTED' ? [...route.slice(0, lastReached + 1), 'REJECTED'] : route
  return <ol aria-label={`Avance de ${application.vacancyTitle}`}
    className="grid gap-3 sm:auto-cols-fr sm:grid-flow-col sm:gap-0">
    {stages.map((stage, index) => {
      const isCurrent = stage === current
      const recorded = reached.has(stage)
      const passed = recorded && !isCurrent
      const closed = isCurrent && (stage === 'REJECTED' || stage === 'HIRED')
      const when = reached.get(stage)
      const nextStage = stages[index + 1]
      const nextRecorded = nextStage !== undefined && reached.has(nextStage)
      return <li key={stage} aria-current={isCurrent ? 'step' : undefined}
        className="relative flex min-w-0 gap-3 sm:flex-col sm:items-center sm:gap-2">
        {index < stages.length - 1 && <span aria-hidden="true" className={cn('absolute top-6 -bottom-3 left-3 w-px sm:top-3 sm:bottom-auto sm:left-1/2 sm:h-px sm:w-full',
          recorded && nextRecorded ? 'bg-high-700' : 'bg-ink-100')} />}
        <span aria-hidden="true" className={cn('relative z-10 grid size-6 shrink-0 place-items-center rounded-full border bg-white',
          stage === 'REJECTED' && isCurrent ? 'border-low-700 bg-low-100 text-low-700'
            : isCurrent && !closed ? 'border-azure-600 bg-azure-100 text-azure-600 ring-2 ring-azure-100 ring-offset-2'
            : passed || closed ? 'border-high-700 bg-high-700 text-white' : 'border-ink-300 text-ink-500')}>
          {stage === 'REJECTED' ? <X className="size-3.5" /> : passed || closed ? <Check className="size-3.5" /> : isCurrent ? <CircleDot className="size-3.5" /> : <span className="text-xs">{index + 1}</span>}
        </span>
        <div className="min-w-0 sm:px-1 sm:text-center">
          <p className={cn('text-xs leading-5', isCurrent ? 'font-semibold text-navy-900' : recorded ? 'font-medium text-ink-700' : 'text-ink-500')}>
            {stageLabel[stage]}{isCurrent && <span className="sr-only"> · Etapa actual</span>}
          </p>
          {when ? <time dateTime={when} className="block text-xs leading-5 text-ink-500">{date.format(new Date(when))}</time>
            : <p className="text-xs leading-5 text-ink-500">{isCurrent || route.indexOf(stage) < route.indexOf(current) ? 'Fecha sin registro' : 'Pendiente'}</p>}
        </div>
      </li>
    })}
  </ol>
}
