import { CalendarDays } from 'lucide-react'
import { useCallback, useContext, useEffect, useState, useSyncExternalStore } from 'react'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { useAuth } from '@/features/auth/auth-context'
import { cn } from '@/lib/cn'
import { navigate, useLocation } from '@/lib/router'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { useResource } from '@/lib/use-resource'
import { interviewsApi, type Interview, type InterviewItem, type InterviewView } from './api'
import { InterviewAgenda } from './interview-agenda'
import { InterviewCalendar, type CalendarMode } from './interview-calendar'
import { InterviewDetail } from './interview-detail'
import { InterviewForm } from './interview-form'
import { InterviewInboxContext } from './interview-inbox-context'
import { interviewState } from './interview-labels'
import { relativeWhen, viewerZone, zoneLabel } from './interview-time'

const VIEWS: InterviewView[] = ['upcoming', 'pending', 'history', 'cancelled', 'updates']
const TAB_LABEL: Record<InterviewView, string> = { upcoming: 'Próximas', pending: 'Por confirmar', history: 'Historial', cancelled: 'Canceladas', updates: 'Cambios sin leer' }
const EMPTY: Record<InterviewView, [string, string]> = {
  upcoming: ['No tienes entrevistas próximas.', 'Cuando una empresa te invite, podrás revisar la hora y confirmarla aquí.'],
  pending: ['No hay invitaciones por responder.', 'Cuando una empresa te proponga una hora, aparecerá aquí.'],
  history: ['Todavía no hay entrevistas pasadas.', 'Aquí quedarán las entrevistas realizadas y las que no se hicieron.'],
  cancelled: ['No hay entrevistas canceladas.', 'Si una empresa cancela una cita, verás aquí el motivo.'],
  updates: ['No hay cambios sin leer.', 'Aquí aparecen las citas que cambiaron desde la última vez que las abriste.'],
}
const EMPTY_COMPANY: Partial<Record<InterviewView, [string, string]>> = {
  upcoming: ['No hay entrevistas próximas.', 'Agenda una desde la ficha de una persona en Candidatos.'],
  pending: ['No hay invitaciones esperando respuesta.', 'Las citas que envíes quedarán aquí hasta que la persona responda.'],
}

export function InterviewsPage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  return <InterviewWorkspace token={state.accessToken} company={state.user.role !== 'CANDIDATE'} />
}

function useWide() {
  return useSyncExternalStore((change) => {
    const media = window.matchMedia?.('(min-width: 1024px)')
    media?.addEventListener('change', change)
    return () => media?.removeEventListener('change', change)
  }, () => window.matchMedia?.('(min-width: 1024px)').matches ?? true)
}

function InterviewWorkspace({ token, company }: { token: string; company: boolean }) {
  const { params } = useLocation()
  const applicationId = company ? params.get('postulacion') : null
  const requested = params.get('vista') as InterviewView | null
  const view: InterviewView = requested && VIEWS.includes(requested) ? requested : 'upcoming'
  const calendar = (['semana', 'mes'] as const).find((m) => m === params.get('calendario')) ?? null
  const chosen = params.get('cita')
  const mine = company && params.get('mias') === '1'
  const anchorParam = params.get('fecha')
  const anchor = anchorParam && /^\d{4}-\d{2}-\d{2}$/.test(anchorParam) ? new Date(`${anchorParam}T12:00:00`) : new Date()
  const wide = useWide()
  const inbox = useContext(InterviewInboxContext)
  const realtime = useRealtime()
  const [page, setPage] = useState(0)
  const [feedback, setFeedback] = useState<string | null>(null)

  const list = useResource(() => interviewsApi.list(token, view, page, mine), `${token}:${view}:${page}:${mine}`, 'No se pudieron cargar las entrevistas.')
  const summary = useResource(() => interviewsApi.summary(token, mine), `${token}:summary:${mine}`, 'No se pudieron cargar los conteos.')
  const { refresh: refreshList } = list
  const { refresh: refreshSummary } = summary
  const refresh = useCallback(() => { refreshList(); refreshSummary() }, [refreshList, refreshSummary])
  useRealtimeNotices((n) => { if (n.type === 'interview.changed' || n.type === 'reconnected') refresh() })
  useEffect(() => {
    if (realtime?.status === 'online') refresh()
  }, [realtime?.status, refresh])
  useEffect(() => {
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [refresh])

  const items = list.state.status === 'ready' ? list.state.data.items : []
  // En escritorio se muestra la primera cita sin marcarla como leída; en móvil, la agenda ocupa la pantalla.
  const selected = chosen ?? (wide && !calendar ? items[0]?.interview.id ?? null : null)
  const counts = summary.state.status === 'ready' ? summary.state.data.counts : null
  const next = summary.state.status === 'ready' ? summary.state.data.next : null

  function go(change: Record<string, string | null>, replace = false) {
    const nextParams = new URLSearchParams(params)
    for (const [key, value] of Object.entries(change)) {
      if (value === null) nextParams.delete(key)
      else nextParams.set(key, value)
    }
    const query = nextParams.toString()
    navigate(`/entrevistas${query ? `?${query}` : ''}`, { replace })
  }

  function scheduled(value: Interview) {
    setFeedback('Invitación enviada. La persona debe confirmar la hora.')
    realtime?.emit({ type: 'interview.changed', interviewId: value.id, applicationId: value.applicationId })
    navigate(`/entrevistas?cita=${value.id}`)
    refresh()
  }

  if (applicationId) return <div className="app-page">
    <NewInterview key={applicationId} token={token} applicationId={applicationId} onSaved={scheduled} />
  </div>

  const overlapFor = (id: string | null): InterviewItem | null => {
    if (company || !id) return null
    const current = items.find((i) => i.interview.id === id)
    if (!current) return null
    const start = Date.parse(current.interview.startsAt)
    const end = start + current.interview.durationMinutes * 60_000
    return items.find((other) => other.interview.id !== id && ['INVITED', 'CONFIRMED'].includes(other.interview.status)
      && Date.parse(other.interview.startsAt) < end && Date.parse(other.interview.startsAt) + other.interview.durationMinutes * 60_000 > start) ?? null
  }

  const showDetail = !!selected && (wide || !!chosen)
  const showList = wide || !chosen
  const tabs = VIEWS.filter((v) => v !== 'updates' || view === 'updates' || (inbox?.unread ?? 0) > 0)
  const countOf = (v: InterviewView) => v === 'updates' ? inbox?.unread : counts?.[v as keyof typeof counts]

  return <div className="flex min-h-dvh w-full max-w-[1440px] flex-col">
    {showList && <>
      <header className="flex flex-wrap items-center gap-x-6 gap-y-3 px-4 pt-6 pb-4 sm:px-8">
        <h1 className="text-2xl font-bold tracking-tight text-navy-900">Entrevistas</h1>
        <span className="text-[0.8125rem] text-ink-500">{zoneLabel(viewerZone())}</span>
        <div role="group" aria-label="Forma de ver las entrevistas" className="flex rounded-[0.625rem] bg-ink-100 p-[3px] sm:ml-auto">
          {([['agenda', 'Agenda'], ['semana', 'Semana'], ['mes', 'Mes']] as const).map(([key, label]) => {
            const active = (calendar ?? 'agenda') === key
            return <button key={key} type="button" aria-pressed={active} onClick={() => go({ calendario: key === 'agenda' ? null : key, cita: null })}
              className={cn('min-h-10 cursor-pointer rounded-lg px-4 text-[0.8125rem] font-semibold', active ? 'bg-white text-navy-900 shadow-soft' : 'text-ink-700 hover:text-navy-900')}>{label}</button>
          })}
        </div>
      </header>

      <NextStrip company={company} next={next} counts={counts} onOpen={(id) => go({ cita: id, calendario: null, vista: 'upcoming' })} />
      {feedback && <div className="px-4 pt-3 sm:px-8"><Notice tone="success">{feedback}</Notice></div>}

      {!calendar && <div className="flex flex-wrap items-center gap-x-7 border-b border-ink-100 px-4 sm:px-8">
        <div role="tablist" aria-label="Filtrar entrevistas" className="flex gap-x-6 overflow-x-auto [scrollbar-width:none] sm:gap-x-7">
          {tabs.map((v) => <button key={v} role="tab" type="button" aria-selected={view === v} onClick={() => { setPage(0); go({ vista: v === 'upcoming' ? null : v, cita: null }) }}
            className={cn('min-h-12 shrink-0 cursor-pointer text-sm font-semibold whitespace-nowrap', view === v ? 'text-navy-900 shadow-[inset_0_-2px_0_var(--color-azure-600)]' : 'text-ink-700 hover:text-navy-900')}>
            {TAB_LABEL[v]} {countOf(v) !== undefined && <span className="font-medium text-ink-500 tabular-nums">{countOf(v)}</span>}
          </button>)}
        </div>
        {company && <MineToggle mine={mine} onChange={(value) => { setPage(0); go({ mias: value ? '1' : null, cita: null }) }} />}
      </div>}
      {calendar && company && <div className="flex justify-end border-b border-ink-100 px-4 sm:px-8"><MineToggle mine={mine} onChange={(value) => go({ mias: value ? '1' : null })} /></div>}
    </>}

    <div className="flex flex-1 flex-wrap items-stretch">
      {showList && <section aria-label={calendar ? 'Calendario' : TAB_LABEL[view]} className="min-w-0 flex-[999_1_28rem] px-4 pb-8 sm:px-8">
        {calendar ? <div className="pt-3"><InterviewCalendar token={token} company={company} mode={calendar as CalendarMode} anchor={anchor} mine={mine}
          onAnchor={(d) => go({ fecha: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }, true)}
          onSelect={(id) => go({ cita: id })} /></div> : <>
          {list.state.status === 'loading' && <p role="status" className="py-6 text-sm text-ink-700">Cargando entrevistas…</p>}
          {list.state.status === 'error' && <div role="alert" className="flex flex-col items-start gap-3 py-6 text-sm text-ink-700">
            <p>No pudimos cargar tus entrevistas. Revisa tu conexión; tus datos siguen guardados.</p>
            <Button variant="secondary" onClick={list.retry}>Intentar de nuevo</Button></div>}
          {list.state.status === 'ready' && items.length === 0 && <div className="py-10">
            <StateMessage icon={CalendarDays} title={(company && EMPTY_COMPANY[view] || EMPTY[view])[0]} description={(company && EMPTY_COMPANY[view] || EMPTY[view])[1]} /></div>}
          {items.length > 0 && <InterviewAgenda items={items} company={company} selected={selected} onSelect={(id) => go({ cita: id })} />}
          {list.state.status === 'ready' && (page > 0 || list.state.data.hasMore) && <nav aria-label="Páginas de entrevistas" className="mt-6 flex flex-wrap items-center gap-3 border-t border-ink-100 pt-4 text-sm">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Anterior</Button>
            <span>Página {page + 1}</span>
            <Button size="sm" variant="secondary" disabled={!list.state.data.hasMore || page >= 10000} onClick={() => setPage((p) => p + 1)}>Siguiente</Button>
          </nav>}
        </>}
      </section>}
      {showDetail && selected && <aside aria-label="Detalle de la entrevista"
        className={cn('min-w-0 flex-[1_1_24rem] px-4 py-6 sm:px-8', showList && 'lg:max-w-[30rem] lg:border-l lg:border-ink-100')}>
        <InterviewDetail token={token} company={company} id={selected} explicit={!!chosen} overlap={overlapFor(selected)}
          onChanged={refresh} onBack={chosen ? () => go({ cita: null }) : undefined} />
      </aside>}
    </div>
  </div>
}

function MineToggle({ mine, onChange }: { mine: boolean; onChange: (value: boolean) => void }) {
  return <label className="ml-auto flex min-h-12 cursor-pointer items-center gap-2 text-sm text-ink-700">
    <input type="checkbox" checked={mine} onChange={(e) => onChange(e.target.checked)} className="size-4 accent-azure-600" />
    Solo las que tengo a cargo
  </label>
}

/** Franja con la próxima cita (y, para la empresa, lo que espera una acción suya). */
function NextStrip({ company, next, counts, onOpen }: {
  company: boolean; next: InterviewItem | null; counts: { changeRequests: number; awaitingOutcome: number; expired: number } | null
  onOpen: (id: string) => void
}) {
  const pendingWork = company && counts ? [
    counts.changeRequests > 0 && `${counts.changeRequests} ${counts.changeRequests === 1 ? 'propuesta de otra hora' : 'propuestas de otra hora'}`,
    counts.awaitingOutcome > 0 && `${counts.awaitingOutcome} ${counts.awaitingOutcome === 1 ? 'resultado sin registrar' : 'resultados sin registrar'}`,
    counts.expired > 0 && `${counts.expired} ${counts.expired === 1 ? 'invitación sin respuesta' : 'invitaciones sin respuesta'}`,
  ].filter(Boolean) as string[] : []
  if (!next && pendingWork.length === 0) return null
  const state = next ? interviewState(next, company) : null
  return <section aria-label="Próxima entrevista" className="flex flex-wrap items-center gap-x-7 gap-y-2 border-y border-azure-100 bg-azure-50 px-4 py-3.5 sm:px-8">
    {next && state && <>
      <strong className="text-lg font-bold text-navy-900 tabular-nums">{relativeWhen(next.interview.startsAt)}</strong>
      <span className="min-w-0 text-sm text-ink-700">{next.interview.title} {company ? `con ${next.candidateEmail}` : `con ${next.companyName}`}, {next.interview.durationMinutes} min</span>
      <span className={cn('text-[0.8125rem] font-semibold', state.tone === 'high' ? 'text-high-700' : state.tone === 'mid' ? 'text-mid-700' : 'text-navy-700')}>{state.label}</span>
    </>}
    {pendingWork.length > 0 && <span className="text-sm font-semibold text-navy-900">Esperan tu acción: {pendingWork.join(', ')}</span>}
    {next && <button type="button" onClick={() => onOpen(next.interview.id)}
      className="inline-flex min-h-11 cursor-pointer items-center text-sm font-semibold text-azure-600 underline underline-offset-4 hover:text-navy-900 sm:ml-auto">Ver detalles</button>}
  </section>
}

function NewInterview({ token, applicationId, onSaved }: { token: string; applicationId: string; onSaved: (value: Interview) => void }) {
  const { state, refresh } = useResource(() => interviewsApi.context(token, applicationId), `${token}:${applicationId}`, 'No se pudo abrir esta postulación.')
  return <section className="flex max-w-3xl flex-col gap-4">
    <h1 className="text-2xl font-bold tracking-tight text-navy-900">Agendar entrevista</h1>
    {state.status === 'loading' && <p role="status">Comprobando postulación…</p>}
    {state.status === 'error' && <Notice tone="error">{state.message} <button className="underline" onClick={refresh}>Reintentar</button></Notice>}
    {state.status === 'ready' && <><p className="text-sm text-ink-700"><strong className="text-ink-900">{state.data.candidateEmail}</strong>, {state.data.vacancyTitle}</p>
      <InterviewForm token={token} applicationId={applicationId} onSaved={onSaved} onClose={() => navigate('/entrevistas')} /></>}
    {state.status !== 'ready' && <Button variant="secondary" className="self-start" onClick={() => navigate('/entrevistas')}>Volver a entrevistas</Button>}
  </section>
}
