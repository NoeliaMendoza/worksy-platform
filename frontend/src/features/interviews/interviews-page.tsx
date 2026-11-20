import { useEffect, useState } from 'react'
import { PageHeader } from '@/components/page-header'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { CalendarDays } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { useAuth } from '@/features/auth/auth-context'
import { ApiError } from '@/features/auth/api'
import { errorMessage } from '@/lib/http'
import { navigate, useLocation } from '@/lib/router'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { useResource } from '@/lib/use-resource'
import { interviewsApi, type Interview, type InterviewItem, type InterviewView } from './api'
import { InterviewForm } from './interview-form'
import { interviewTime } from './interview-time'

const statusLabel = { INVITED: 'Pendiente de confirmar', CONFIRMED: 'Confirmada', DECLINED: 'Rechazada', CANCELLED: 'Cancelada' }
const changeLabel: Record<string, string> = { SCHEDULED: 'Invitación creada', UPDATED: 'Invitación actualizada', CONFIRMED: 'Confirmada', DECLINED: 'Rechazada', CANCELLED: 'Cancelada' }

export function InterviewsPage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  return <InterviewWorkspace token={state.accessToken} company={state.user.role !== 'CANDIDATE'} />
}

function InterviewWorkspace({ token, company }: { token: string; company: boolean }) {
  const { params } = useLocation()
  const applicationId = company ? params.get('postulacion') : null
  const requested = params.get('vista')
  const view: InterviewView = requested === 'updates' || requested === 'history' ? requested : 'upcoming'
  const [page, setPage] = useState(0)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const { state, refresh, retry } = useResource(() => interviewsApi.list(token, view, page), `${token}:${view}:${page}`, 'No se pudieron cargar las entrevistas.')
  const realtime = useRealtime()
  useRealtimeNotices((n) => { if (n.type === 'interview.changed' || n.type === 'reconnected') refresh() })
  useEffect(() => {
    if (realtime?.status === 'online') refresh()
  }, [realtime?.status, refresh])
  useEffect(() => {
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [refresh])
  function saved(value: Interview) {
    setFeedback({ tone: 'success', text: 'Invitación guardada. La persona debe confirmar los datos actuales.' })
    realtime?.emit({ type: 'interview.changed', interviewId: value.id, applicationId: value.applicationId })
    navigate('/entrevistas')
    refresh()
  }
  return <div className="app-page">
    <PageHeader title="Entrevistas" description="Consulta la hora y su zona antes de confirmar. Los avisos y respuestas se guardan en Worksy." />
    {feedback && <Notice tone={feedback.tone}>{feedback.text}</Notice>}
    {feedback?.tone === 'error' && <Button variant="secondary" size="sm" className="self-start" onClick={() => { setFeedback(null); retry() }}>Reintentar consulta</Button>}
    {applicationId ? <NewInterview key={applicationId} token={token} applicationId={applicationId} onSaved={saved} /> : <>
      <nav aria-label="Vistas de entrevistas" className="flex flex-wrap gap-2 border-b border-ink-100 pb-3">
        {([['upcoming', 'Próximas'], ['updates', 'Cambios sin leer'], ['history', 'Historial']] as const).map(([key, label]) =>
          <Button key={key} size="sm" variant={view === key ? 'primary' : 'secondary'} aria-current={view === key ? 'page' : undefined}
            onClick={() => { setPage(0); navigate(`/entrevistas?vista=${key}`) }}>{label}</Button>)}
      </nav>
      {company && <p className="text-sm text-ink-500">Para invitar a una persona, abre una vacante y pulsa «Agendar entrevista» en su postulación.</p>}
      {state.status === 'loading' && <p role="status">Cargando entrevistas…</p>}
      {state.status === 'error' && <Notice tone="error">{state.message} <button className="underline" onClick={refresh}>Reintentar</button></Notice>}
      {state.status === 'ready' && <>
        {state.data.items.length === 0 && <StateMessage icon={CalendarDays} title="No hay entrevistas en esta vista."
          description={view === 'updates' ? 'Aquí aparecerán los cambios que aún no hayas leído.' : view === 'history' ? 'Aquí podrás consultar las entrevistas anteriores y canceladas.' : company ? 'Puedes enviar una invitación desde las postulaciones de una vacante.' : 'Cuando una empresa te invite, podrás revisar los datos y confirmar tu asistencia aquí.'} />}
        {state.data.items.map((item) => <InterviewCard key={`${item.interview.id}:${item.interview.version}`} token={token} company={company} item={item}
          onRefresh={refresh} onResult={setFeedback} />)}
        {(page > 0 || state.data.hasMore) && <nav aria-label="Páginas de entrevistas" className="flex flex-wrap items-center gap-3 border-t border-ink-100 pt-4 text-sm"><Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Anterior</Button>
          <span>Página {page + 1}</span><Button size="sm" variant="secondary" disabled={!state.data.hasMore || page >= 10000} onClick={() => setPage((p) => p + 1)}>Siguiente</Button></nav>}
      </>}
    </>}
  </div>
}

function NewInterview({ token, applicationId, onSaved }: { token: string; applicationId: string; onSaved: (value: Interview) => void }) {
  const { state, refresh } = useResource(() => interviewsApi.context(token, applicationId), `${token}:${applicationId}`, 'No se pudo abrir esta postulación.')
  return <section className="flex flex-col gap-4">
    {state.status === 'loading' && <p role="status">Comprobando postulación…</p>}
    {state.status === 'error' && <Notice tone="error">{state.message} <button className="underline" onClick={refresh}>Reintentar</button></Notice>}
    {state.status === 'ready' && <><p><strong>{state.data.vacancyTitle}</strong> · {state.data.candidateEmail}</p>
      <InterviewForm token={token} applicationId={applicationId} onSaved={onSaved} onClose={() => navigate('/entrevistas')} /></>}
    {state.status !== 'ready' && <Button variant="secondary" onClick={() => navigate('/entrevistas')}>Volver a entrevistas</Button>}
  </section>
}

function InterviewCard({ token, company, item, onRefresh, onResult }: {
  token: string; company: boolean; item: InterviewItem; onRefresh: () => void
  onResult: (result: { tone: 'success' | 'error'; text: string }) => void
}) {
  const interview = item.interview
  const realtime = useRealtime()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [sending, setSending] = useState(false)
  const [stale, setStale] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])
  const active = ['INVITED', 'CONFIRMED'].includes(interview.status) && new Date(interview.startsAt).getTime() > now
  function changed(value: Interview, text: string) {
    onResult({ tone: 'success', text })
    realtime?.emit({ type: 'interview.changed', interviewId: value.id, applicationId: value.applicationId })
    setEditing(false)
    onRefresh()
  }
  async function act(action: 'CONFIRMED' | 'DECLINED' | 'CANCELLED') {
    if (sending || stale) return
    setSending(true)
    try {
      const value = action === 'CANCELLED' ? await interviewsApi.cancel(token, interview.id, interview.version)
        : await interviewsApi.respond(token, interview.id, interview.version, action)
      changed(value, action === 'CANCELLED' ? 'Entrevista cancelada en Worksy. Si creaste una reunión externa, cancélala también en ese servicio.' : 'Respuesta guardada.')
    } catch (err) {
      setStale(true)
      onResult({ tone: 'error', text: err instanceof ApiError && err.status === 409 ? 'La entrevista cambió. Actualiza la lista y revisa sus datos antes de responder.'
        : `${errorMessage(err, 'No se pudo guardar la respuesta.')} Actualiza la lista para comprobar el estado antes de reintentar.` })
      onRefresh()
    } finally { setSending(false) }
  }
  return <article className="flex min-w-0 flex-col items-start gap-3 break-words rounded-card border border-ink-100 bg-white p-4 text-sm shadow-soft [&>p]:max-w-full">
    <div className="flex w-full min-w-0 flex-wrap items-start justify-between gap-2"><h2 className="min-w-0 text-base font-semibold">{interview.title}</h2><Badge tone={interview.status === 'CONFIRMED' ? 'high' : interview.status === 'INVITED' ? 'azure' : 'neutral'}>{statusLabel[interview.status]}</Badge></div>
    <p className="text-ink-700">{item.companyName} · {item.vacancyTitle}{company ? ` · ${item.candidateEmail}` : ''}</p>
    <p className="font-medium">{interviewTime(interview.startsAt, interview.zoneId)} · {interview.durationMinutes} minutos</p>
    <p className="text-sm text-ink-500">Zona: {interview.zoneId}</p>
    <Button variant="secondary" aria-expanded={open} onClick={() => setOpen(!open)}>{open ? 'Ocultar detalles' : item.unread ? 'Ver detalles y cambios sin leer' : 'Ver detalles'}</Button>
    {open && <>
      {interview.mode === 'ONLINE' ? <a className="break-all text-azure-700 underline" href={interview.place} target="_blank" rel="noopener noreferrer">Abrir reunión (otra pestaña)</a> : <p>Dirección: {interview.place}</p>}
      {interview.note && <p className="whitespace-pre-wrap break-words">{interview.note}</p>}
      <ReadInterview token={token} interview={interview} unread={item.unread} />
      <InterviewHistory token={token} interview={interview} />
      {active && <div className="flex flex-wrap gap-3">
        {company ? <><Button variant="secondary" disabled={sending || stale} onClick={() => setEditing(!editing)}>Reprogramar o editar</Button>
          <Button variant="secondary" disabled={sending || stale} onClick={() => void act('CANCELLED')}>Cancelar entrevista</Button></>
          : interview.status === 'INVITED' && <><Button disabled={sending || stale} onClick={() => void act('CONFIRMED')}>Confirmar asistencia</Button>
            <Button variant="secondary" disabled={sending || stale} onClick={() => void act('DECLINED')}>Rechazar invitación</Button></>}
      </div>}
      {editing && <InterviewForm token={token} current={interview} applicationId={interview.applicationId}
        onSaved={(value) => changed(value, 'Invitación actualizada. La persona debe confirmar de nuevo.')} onClose={() => setEditing(false)} />}
    </>}
  </article>
}

function ReadInterview({ token, interview, unread }: { token: string; interview: Interview; unread: boolean }) {
  const realtime = useRealtime()
  const [attempt, setAttempt] = useState(0)
  const [read, setRead] = useState(!unread)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    if (read) return
    let active = true
    let pending = false
    function mark() {
      if (document.visibilityState !== 'visible' || pending) return
      pending = true
      interviewsApi.read(token, interview.id, interview.version).then(() => {
        if (!active) return
        setRead(true)
        setFailed(false)
        realtime?.emit({ type: 'interview.read', interviewId: interview.id })
      }, () => { if (active) setFailed(true) })
    }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => { active = false; document.removeEventListener('visibilitychange', mark) }
  }, [token, interview.id, interview.version, read, attempt, realtime])
  return failed ? <p role="status">No se pudo guardar la lectura. <button className="underline" onClick={() => setAttempt((n) => n + 1)}>Reintentar lectura</button></p>
    : <p className="text-sm text-ink-500">{read ? 'Cambios leídos.' : 'Cambios pendientes de lectura.'}</p>
}

function InterviewHistory({ token, interview }: { token: string; interview: Interview }) {
  const { state, refresh } = useResource(() => interviewsApi.history(token, interview.id), `${token}:${interview.id}:${interview.version}`, 'No se pudo cargar el historial.')
  return <section aria-label="Historial de la entrevista" className="border-t border-ink-100 pt-3">
    <h3 className="font-semibold">Historial de cambios</h3>
    {state.status === 'loading' && <p role="status">Cargando historial…</p>}
    {state.status === 'error' && <p>{state.message} <button className="underline" onClick={refresh}>Reintentar historial</button></p>}
    {state.status === 'ready' && <ol className="space-y-2 text-sm">{state.data.map((e) => <li key={e.version}>{changeLabel[e.change] ?? e.change} · {interviewTime(e.changedAt, e.zoneId)}<br />Cita: {interviewTime(e.startsAt, e.zoneId)} · {e.durationMinutes} min</li>)}</ol>}
  </section>
}
