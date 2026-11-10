import { cn } from '@/lib/cn'

type FilterOption<T extends string> = { value: T; label: string; count: number }

type FilterBarProps<T extends string> = {
  label: string
  options: FilterOption<T>[]
  value: T
  onChange: (value: T) => void
  className?: string
}

/** Filtros en píldora con contador. Botones conmutables, desplazables en móvil. */
export function FilterBar<T extends string>({ label, options, value, onChange, className }: FilterBarProps<T>) {
  return (
    <div role="group" aria-label={label} className={cn('-mx-1 flex gap-1.5 overflow-x-auto px-1 py-1 [scrollbar-width:none]', className)}>
      {options.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(o.value)}
            className={cn(
              'inline-flex h-9 shrink-0 cursor-pointer items-center gap-2 rounded-full px-4 text-sm font-medium transition-colors',
              active ? 'bg-navy-900 text-white' : 'bg-white text-ink-700 shadow-soft hover:text-navy-900',
            )}
          >
            {o.label}
            <span className="sr-only">, </span>
            <span
              className={cn(
                'min-w-5 rounded-full px-1.5 text-xs font-semibold tabular-nums',
                active ? 'bg-white/15 text-white' : 'bg-azure-100 text-navy-700',
              )}
            >
              {o.count}
            </span>
          </button>
        )
      })}
    </div>
  )
}
