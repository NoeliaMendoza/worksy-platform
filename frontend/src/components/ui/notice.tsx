import { AlertCircle, CheckCircle2 } from 'lucide-react'
import { useEffect, useRef, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

type NoticeProps = {
  tone: 'error' | 'success'
  children: ReactNode
  /** Mueve el foco al aviso al aparecer (para que el lector de pantalla lo lea y el teclado siga desde ahí). */
  autoFocus?: boolean
  id?: string
  className?: string
}

/** Aviso en línea. Error: role="alert". Éxito: role="status". */
export function Notice({ tone, children, autoFocus = false, id, className }: NoticeProps) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (autoFocus) ref.current?.focus()
  }, [autoFocus, children])

  const Icon = tone === 'error' ? AlertCircle : CheckCircle2
  return (
    <div
      id={id}
      ref={ref}
      tabIndex={-1}
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'flex gap-3 rounded-2xl p-4 text-sm leading-relaxed focus:outline-none',
        tone === 'error' ? 'bg-low-100 text-low-700' : 'bg-high-100 text-high-700',
        className,
      )}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>{children}</div>
    </div>
  )
}
