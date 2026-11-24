import { useContext } from 'react'
import { Button } from '@/components/ui/button'
import { AppLink } from '@/components/app-link'
import { navigate } from '@/lib/router'
import { stageLabel } from './stage-labels'
const noticeDate = new Intl.DateTimeFormat('es',{dateStyle:'medium',timeStyle:'short'})
import { ApplicationUpdatesContext } from './application-updates-context'

/** Visible desde cualquier pantalla del candidato; se mantiene hasta consultar el seguimiento. */
export function ApplicationUpdateNotice({ showError = true, embedded = false }: { showError?: boolean; embedded?: boolean }) {
  const updates = useContext(ApplicationUpdatesContext)
  if (!updates) return null
  if (updates.state.status === 'error') return showError ? (
    <div role="status" className="rounded-xl border border-ink-100 bg-white px-4 py-3 text-sm text-ink-700">
      No pudimos consultar los avisos de tus postulaciones. <button className="underline" onClick={updates.refresh}>Volver a consultar avisos</button>
    </div>
  ) : null
  if (updates.unread === 0) return null
  const recent = updates.state.status === 'ready' ? [...updates.state.data.updates].sort((a,b)=>Date.parse(b.changedAt)-Date.parse(a.changedAt)).slice(0,5) : []
  return (
      <div className={embedded ? 'px-4 py-3 text-sm text-navy-900' : 'rounded-card border border-azure-200 bg-white px-4 py-3 text-sm text-navy-900'}>
        <div role="status" className="flex flex-wrap items-center justify-between gap-3">
        <p>{updates.unread === 1 ? 'Tienes 1 cambio de etapa sin leer.' : `Tienes ${updates.unread} cambios de etapa sin leer.`}</p>
        <Button variant="ghost" size="sm" onClick={() => navigate('/postulaciones')}>Revisar postulaciones</Button>
        </div>
        {recent.length>0&&<details className="mt-2 border-t border-ink-100 pt-2">
          <summary className="min-h-8 cursor-pointer py-1 text-xs font-semibold">Ver procesos con novedades</summary>
          <ul className="mt-2 divide-y divide-ink-100" aria-label="Procesos con cambios sin leer">{recent.map((item)=><li key={item.applicationId} className="min-w-0 space-y-1 py-3">
            <AppLink className="inline-flex min-h-6 break-words" href={`/postulaciones?postulacion=${encodeURIComponent(item.applicationId)}`}>{item.vacancyTitle} · {item.companyName}</AppLink>
            <p className="text-xs text-ink-700">{stageLabel[item.stage]} · <time dateTime={item.changedAt}>{noticeDate.format(new Date(item.changedAt))}</time> · {item.unread} {item.unread===1?'cambio sin leer':'cambios sin leer'}</p>
          </li>)}</ul>
          <p className="mt-2 text-xs leading-5 text-ink-500">Se muestran hasta 5 procesos. Abre su seguimiento para leer los cambios; desplegar esta lista no los marca como leídos.</p>
        </details>}
      </div>
  )
}
