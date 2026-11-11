import { useId } from 'react'
import { cn } from '@/lib/cn'

type ChoicePillsProps<T extends string> = {
  legend: string
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
  className?: string
}

/**
 * Grupo de opciones excluyentes con aspecto de píldora. Son radios nativos dentro de un fieldset:
 * flechas para moverse, leyenda anunciada y estado "seleccionado" sin ARIA extra.
 */
export function ChoicePills<T extends string>({ legend, value, options, onChange, className }: ChoicePillsProps<T>) {
  const name = useId()
  return (
    <fieldset className={cn('flex flex-col gap-2', className)}>
      <legend className="mb-2 text-sm font-semibold text-ink-900">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((o) => (
          <label
            key={o.value}
            className={cn(
              'inline-flex h-10 cursor-pointer items-center rounded-full px-4 text-sm font-medium ring-1 transition-colors ring-inset',
              'has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-azure-500',
              value === o.value ? 'bg-navy-900 text-white ring-navy-900' : 'bg-white text-ink-700 ring-ink-300 hover:ring-azure-400',
            )}
          >
            <input
              type="radio"
              name={name}
              value={o.value}
              checked={value === o.value}
              onChange={() => onChange(o.value)}
              className="sr-only"
            />
            {o.label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
