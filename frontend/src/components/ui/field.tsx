import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type FieldProps = {
  id: string
  label: string
  /** Mensaje de error del campo; se enlaza con el control por `${id}-error`. */
  error?: string
  /** Acción junto a la etiqueta (p. ej., mostrar la contraseña). */
  aside?: ReactNode
  children: ReactNode
  className?: string
}

/** Etiqueta + control + error. El control debe usar el mismo `id` y `aria-describedby={`${id}-error`}`. */
export function Field({ id, label, error, aside, children, className }: FieldProps) {
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex items-center justify-between gap-3">
        <label htmlFor={id} className="text-sm font-semibold text-ink-900">
          {label}
        </label>
        {aside}
      </div>
      {children}
      <p id={`${id}-error`} className={cn('text-sm text-low-700', !error && 'sr-only')}>
        {error}
      </p>
    </div>
  )
}
