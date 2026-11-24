import { useId, type ReactNode } from 'react'

/** Mantiene el desplazamiento en la tabla, con nombre y ayuda para teclado. */
export function TableRegion({ label, children }: { label: string; children: ReactNode }) {
  const hintId = useId()
  return <div className="min-w-0">
    <p id={hintId} className="mb-2 text-xs leading-5 text-ink-500 sm:sr-only">Desplaza la tabla horizontalmente para ver todas las columnas. Con teclado, enfoca la tabla y usa las flechas.</p>
    <div role="region" aria-label={label} aria-describedby={hintId} tabIndex={0} className="relative overflow-x-auto rounded-card border border-ink-100 bg-white shadow-soft">
      {children}
    </div>
  </div>
}
