import { useId, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** Panel compacto, con un encabezado accesible y una acción de sección opcional. */
export function SectionCard({ title, action, children, className }: {
  title: string; action?: ReactNode; children: ReactNode; className?: string
}) {
  const id = useId()
  return <section aria-labelledby={id} className={cn('min-w-0 overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft', className)}>
    <header className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-ink-100 px-4 py-2">
      <h2 id={id} className="text-sm font-semibold text-ink-900">{title}</h2>
      {action}
    </header>
    <div className="p-4">{children}</div>
  </section>
}
