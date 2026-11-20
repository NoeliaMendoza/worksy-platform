import type { ReactNode } from 'react'

/** Jerarquía compartida del espacio de trabajo; las acciones quedan separadas del título. */
export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return <header className="flex flex-wrap items-start justify-between gap-3 border-b border-ink-100 pb-4">
    <div className="min-w-0">
      <h1 className="break-words text-2xl font-semibold tracking-tight text-navy-900">{title}</h1>
      {description && <p className="mt-1 max-w-[65ch] text-sm leading-relaxed text-ink-700">{description}</p>}
    </div>
    {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
  </header>
}
