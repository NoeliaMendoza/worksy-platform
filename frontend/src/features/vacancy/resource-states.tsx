import { ServerCrash } from 'lucide-react'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

/** Carga y error comunes de las pantallas de vacantes. */
export function LoadingRows({ rows = 3 }: { rows?: number }) {
  return (
    <>
      <p role="status" className="sr-only">
        Cargando…
      </p>
      <div className="flex flex-col gap-3" aria-busy="true">
        {Array.from({ length: rows }, (_, i) => (
          <Skeleton key={i} className="h-20 rounded-card" />
        ))}
      </div>
    </>
  )
}

export function LoadError({ title, message, onRetry }: { title: string; message: string; onRetry: () => void }) {
  return (
    <StateMessage
      tone="error"
      icon={ServerCrash}
      title={title}
      description={message}
      action={<Button onClick={onRetry}>Reintentar</Button>}
    />
  )
}
