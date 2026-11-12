import { Check, Clock3, X } from 'lucide-react'
import { decisionAction } from '@/features/ranking/labels'
import type { Decision } from '@/features/ranking/types'
import { cn } from '@/lib/cn'

const options: { value: Decision; Icon: typeof Check; active: string }[] = [
  { value: 'preseleccionado', Icon: Check, active: 'bg-navy-900 text-white ring-navy-900' },
  { value: 'en_espera', Icon: Clock3, active: 'bg-mid-100 text-mid-700 ring-mid-700' },
  { value: 'descartado', Icon: X, active: 'bg-low-100 text-low-700 ring-low-700' },
]

type DecisionControlProps = {
  value: Decision | null
  /** Recibe `null` cuando se pulsa de nuevo la opción activa (vuelve a "sin revisar"). */
  onChange: (next: Decision | null) => void
  candidateName: string
  className?: string
  disabled?: boolean
}

/**
 * Decisión humana sobre un CV. Siempre visible: el modelo ordena, la persona decide.
 * Botones conmutables (aria-pressed) con texto e icono.
 */
export function DecisionControl({ value, onChange, candidateName, className, disabled }: DecisionControlProps) {
  return (
    <div role="group" aria-label={`Decisión sobre ${candidateName}`} className={cn('flex flex-wrap gap-1 sm:gap-1.5', className)}>
      {options.map(({ value: option, Icon, active }) => {
        const pressed = value === option
        return (
          <button
            key={option}
            type="button"
            disabled={disabled}
            aria-pressed={pressed}
            onClick={() => onChange(pressed ? null : option)}
            className={cn(
              'inline-flex h-10 flex-1 cursor-pointer items-center justify-center gap-1 rounded-full px-1.5 text-xs whitespace-nowrap sm:h-9 sm:flex-none sm:gap-1.5 sm:px-3.5 font-semibold ring-1 transition-colors ring-inset',
              pressed ? active : 'bg-white text-ink-700 ring-ink-100 hover:bg-azure-50 hover:text-navy-900 hover:ring-azure-300',
            )}
          >
            <Icon className="size-3.5 shrink-0" aria-hidden="true" />
            {decisionAction[option]}
          </button>
        )
      })}
    </div>
  )
}
