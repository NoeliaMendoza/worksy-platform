import { cn } from '@/lib/cn'

type ScoreMeterProps = {
  /** Valor de 0 a 100. */
  value: number
  label: string
  className?: string
}

/** Compatibilidad CV–vacante en una línea: barra corta y número grande. */
export function ScoreMeter({ value, label, className }: ScoreMeterProps) {
  const v = Math.max(0, Math.min(100, Math.round(value)))
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={v}
        aria-valuetext={`${v} %`}
        className="h-1.5 w-16 overflow-hidden rounded-full bg-azure-100"
      >
        <div className="h-full rounded-full bg-azure-500" style={{ width: `${v}%` }} />
      </div>
      <p className="text-navy-900 tabular-nums" aria-hidden="true">
        <span className="text-2xl font-light tracking-tight">{v}</span>
        <span className="text-sm text-ink-500"> %</span>
      </p>
    </div>
  )
}
