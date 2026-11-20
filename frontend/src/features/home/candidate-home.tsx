import { PageHeader } from '@/components/page-header'
import { Building2, ArrowUpRight, FileSearch, MessageSquare } from 'lucide-react'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import { applicationsApi, type MyApplication } from '@/features/application/api'
import { ApplicationProgressPanel } from '@/features/application/application-progress'
import { ApplicationJourney } from '@/features/application/application-journey'
import { stageLabel } from '@/features/application/stage-labels'
import { formatDate } from '@/features/vacancy/labels'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { useContext, useState } from 'react'
import { cvApi } from '@/features/cv/api'
import { cn } from '@/lib/cn'
import { ApplicationUpdatesContext } from '@/features/application/application-updates-context'
import { useRealtimeNotices } from '@/lib/realtime-context'

type Filter = 'all' | 'active' | 'finished'
const finished = (a: MyApplication) => a.stage === 'HIRED' || a.stage === 'REJECTED'

/** Inicio de la persona candidata: sus postulaciones. Nunca muestra rankings ni datos de otras personas. */
export function CandidateHome() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const { state, retry, refresh } = useResource(() => applicationsApi.mine(token), `mias:${token}`, 'No se pudieron cargar tus postulaciones.')
  const updates = useContext(ApplicationUpdatesContext)
  const [filter, setFilter] = useState<Filter>('all')
  const all = state.status === 'ready' ? state.data : []
  const activeCount = all.filter((a) => !finished(a)).length
  const filtered = all.filter((a) => filter === 'all' || (filter === 'finished' ? finished(a) : !finished(a)))
  useRealtimeNotices((notice) => {
    if (notice.type === 'application.stage.changed' || notice.type === 'reconnected') refresh()
  })

  return (
    <div className="app-page">
      <PageHeader title="Mis postulaciones" description="Consulta el avance de tus procesos y las novedades de cada empresa." />

      {state.status === 'loading' && <LoadingRows rows={2} />}
      {state.status === 'error' && (
        <LoadError title="No se pudieron cargar tus postulaciones" message={state.message} onRetry={retry} />
      )}
      {state.status === 'ready' &&
        (state.data.length === 0 ? (
          <EmptyApplications token={token} />
        ) : (
          <>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 pb-3">
            <div role="group" aria-label="Filtrar postulaciones" className="flex flex-wrap gap-1 rounded-lg bg-white p-1 ring-1 ring-ink-100">
              {([['all', 'Todas', all.length], ['active', 'En curso', activeCount], ['finished', 'Finalizadas', all.length - activeCount]] as const).map(([value, label, count]) =>
                <button key={value} aria-pressed={filter === value} onClick={() => setFilter(value)}
                  className={cn('min-h-10 cursor-pointer rounded-lg px-3 py-2 text-sm transition-colors', filter === value ? 'bg-navy-900 font-semibold text-white' : 'text-ink-700 hover:bg-azure-50')}>
                  {label}{' '}<span className="ml-2 text-xs">{count}</span>
                </button>)}
            </div>
            <p className="text-xs text-ink-500">Una vacante cerrada no implica que tu postulación fue rechazada.</p>
          </div>
          {filtered.length === 0 ? <StateMessage icon={FileSearch} title={filter === 'finished' ? 'No tienes procesos finalizados' : 'No tienes procesos en curso'}
            description="Puedes consultar el resto de tus postulaciones en «Todas»." className="rounded-card py-8"
            action={<Button variant="secondary" size="sm" onClick={() => setFilter('all')}>Ver todas</Button>} /> :
          <ul aria-label="Tus postulaciones" className="divide-y divide-ink-100 overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft">
            {filtered.map((a) => (
              <li key={a.id}>
                {updates?.state.status === 'ready' && updates.state.data.updates.some((u) => u.applicationId === a.id) && (
                  <p className="px-5 pt-4 text-xs font-semibold text-azure-600 sm:px-6">Novedades sin leer en el seguimiento</p>
                )}
                <ApplicationRow application={a} token={token} />
              </li>
            ))}
          </ul>}
          </>
        ))}

      <details className="rounded-lg border border-ink-100 bg-white px-4 py-3 text-sm">
        <summary className="cursor-pointer font-medium text-ink-700">¿Cómo se revisan mis postulaciones?</summary>
        <div className="mt-3 max-w-2xl space-y-2 leading-relaxed text-ink-700">
          <p>El análisis del CV ayuda a organizar la revisión. La decisión final la toma una persona; ningún CV se descarta automáticamente.</p>
          <p>La empresa actualiza la etapa de tu proceso. En «Ver seguimiento» puedes consultar los cambios y los mensajes que comparta contigo.</p>
        </div>
      </details>
    </div>
  )
}

/** Una consulta auxiliar fallida no se interpreta como ausencia de CV. */
function EmptyApplications({ token }: { token: string }) {
  const { state, refresh } = useResource(async () => {
    const [cvs, consents] = await Promise.all([cvApi.list(token), cvApi.consents(token)])
    return { hasCv: cvs.length > 0, shared: consents.shareWithRecruiters }
  }, `preparacion:${token}`, 'No pudimos comprobar tu CV y sus permisos.')
  return <section aria-labelledby="sin-postulaciones" className="rounded-card border border-ink-100 bg-white p-5 shadow-soft">
    <div className="flex flex-col gap-4 sm:flex-row sm:gap-5">
      <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-azure-100 text-navy-700"><FileSearch className="size-5" aria-hidden="true" /></span>
      <div className="max-w-xl">
        <h2 id="sin-postulaciones" className="text-lg font-semibold text-ink-900">Aún no te has postulado</h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-700">Encuentra una vacante y envía tu postulación desde su detalle. Aquí podrás seguir cada proceso.</p>
        {state.status === 'ready' && <p className="mt-3 text-sm text-ink-700">{!state.data.hasCv ? 'Antes de postular, sube tu CV en PDF.' : !state.data.shared
          ? 'Ya tienes un CV. Revisa el permiso para compartirlo con las empresas a las que te postules.'
          : 'Tu CV y el permiso para compartirlo están listos. Elige el archivo al postular.'}</p>}
        {state.status === 'loading' && <p role="status" className="mt-3 text-sm text-ink-500">Comprobando tu CV y sus permisos…</p>}
        {state.status === 'error' && <p role="status" className="mt-3 text-sm text-ink-700">{state.message} <button className="underline" onClick={refresh}>Reintentar comprobación</button></p>}
        <div className="mt-5 flex flex-wrap gap-3">
          <Button onClick={() => navigate('/vacantes')}>Explorar vacantes</Button>
          {state.status === 'ready' && (!state.data.hasCv || !state.data.shared) && <Button variant="secondary" onClick={() => navigate('/mi-cv')}>
            {!state.data.hasCv ? 'Subir mi CV' : 'Revisar permisos del CV'}
          </Button>}
        </div>
      </div>
    </div>
  </section>
}

function ApplicationRow({ application: a, token }: { application: MyApplication; token: string }) {
  const open = a.vacancyStatus === 'PUBLISHED'
  const latest = a.milestones?.at(-1)
  const title = <span className="break-words text-base font-semibold leading-6 text-ink-900">{a.vacancyTitle}</span>
  return <>
    <div className="grid gap-4 p-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.8fr)] xl:gap-6">
      <div className="min-w-0">
        {open ? <a href={`/vacantes/${a.vacancyId}`} className="inline-flex items-start gap-2 hover:underline"
          onClick={(e) => {
            if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
            e.preventDefault(); navigate(`/vacantes/${a.vacancyId}`)
          }}>{title}<ArrowUpRight className="mt-1 size-4 shrink-0 text-ink-500" aria-hidden="true" /></a> : <p>{title}</p>}
        <p className="mt-2 flex items-center gap-1.5 break-words text-sm text-ink-700"><Building2 className="size-3.5 shrink-0" aria-hidden="true" />{a.companyName}</p>
        <p className="mt-2 text-xs leading-5 text-ink-500">Postulación enviada el <time dateTime={a.appliedAt}>{formatDate(a.appliedAt)}</time></p>
        {!open && <div className="mt-2"><Badge tone="neutral">Vacante cerrada</Badge></div>}
      </div>
      <div className="min-w-0 rounded-lg bg-azure-50 p-4">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <Badge tone={a.stage === 'HIRED' ? 'high' : a.stage === 'REJECTED' ? 'low' : 'azure'}>{stageLabel[a.stage ?? 'RECEIVED']}</Badge>
          {latest && <p className="text-xs text-ink-500">Último registro: <time dateTime={latest.changedAt}>{formatDate(latest.changedAt)}</time></p>}
        </div>
        <ApplicationJourney application={a} />
        {a.stage === 'INTERVIEW' && <p className="mt-4 border-t border-ink-100 pt-3 text-xs leading-5 text-ink-700">
          Esta etapa no confirma una cita. <button className="cursor-pointer font-semibold text-azure-600 underline" onClick={() => navigate('/entrevistas')}>Consultar entrevistas</button>
        </p>}
      </div>
    </div>
    <ApplicationProgressPanel token={token} applicationId={a.id} label={a.vacancyTitle}
      actions={<Button variant="ghost" size="sm" aria-label={`Mensajes de ${a.vacancyTitle}`} onClick={() => navigate(`/mensajes/${a.id}`)}><MessageSquare aria-hidden="true" />Mensajes</Button>} />
  </>
}
