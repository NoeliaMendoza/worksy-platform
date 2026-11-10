import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type StateMessageProps = {
  icon: LucideIcon
  title: string
  description: ReactNode
  action?: ReactNode
  tone?: 'neutral' | 'error'
  className?: string
}

/** Estado vacío o de error: qué pasó y qué hacer ahora. */
export function StateMessage({ icon: Icon, title, description, action, tone = 'neutral', className }: StateMessageProps) {
  return (
    <div
      role={tone === 'error' ? 'alert' : undefined}
      className={cn('flex flex-col items-center rounded-panel bg-white px-6 py-14 text-center shadow-soft', className)}
    >
      <span
        className={cn(
          'grid size-12 place-items-center rounded-2xl',
          tone === 'error' ? 'bg-low-100 text-low-700' : 'bg-azure-100 text-navy-700',
        )}
      >
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <h3 className="mt-4 text-lg font-semibold text-ink-900">{title}</h3>
      <p className="mt-1.5 max-w-md text-sm leading-relaxed text-ink-500">{description}</p>
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}
