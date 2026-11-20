import { useContext } from 'react'
import { Button } from '@/components/ui/button'
import { navigate } from '@/lib/router'
import { ApplicationUpdatesContext } from './application-updates-context'

/** Visible desde cualquier pantalla del candidato; se mantiene hasta consultar el seguimiento. */
export function ApplicationUpdateNotice({ showError = true }: { showError?: boolean }) {
  const updates = useContext(ApplicationUpdatesContext)
  if (!updates) return null
  if (updates.state.status === 'error') return showError ? (
    <div role="status" className="rounded-xl border border-ink-100 bg-white px-4 py-3 text-sm text-ink-700">
      No pudimos consultar los avisos de tus postulaciones. <button className="underline" onClick={updates.refresh}>Volver a consultar avisos</button>
    </div>
  ) : null
  if (updates.unread === 0) return null
  return (
      <div role="status" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-azure-200 bg-white px-4 py-2.5 text-sm text-navy-900">
        <p>{updates.unread === 1 ? 'Tienes 1 cambio de etapa sin leer.' : `Tienes ${updates.unread} cambios de etapa sin leer.`}</p>
        <Button variant="ghost" size="sm" onClick={() => navigate('/postulaciones')}>Revisar postulaciones</Button>
      </div>
  )
}
