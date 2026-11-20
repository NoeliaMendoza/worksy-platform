import { ArrowRight, BriefcaseBusiness, CalendarDays, FileText, MessageSquare, type LucideIcon } from 'lucide-react'
import { useCallback, useContext, useEffect, useState } from 'react'
import { AppLink } from '@/components/app-link'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { SectionCard } from '@/components/ui/section-card'
import { PageHeader } from '@/components/page-header'
import { useAuth } from '@/features/auth/auth-context'
import { applicationsApi } from '@/features/application/api'
import { ApplicationUpdatesContext } from '@/features/application/application-updates-context'
import { stageLabel } from '@/features/application/stage-labels'
import { cvApi, MAX_CVS } from '@/features/cv/api'
import { interviewsApi } from '@/features/interviews/api'
import { interviewTime } from '@/features/interviews/interview-time'
import { messagesApi } from '@/features/messages/api'
import { formatDate } from '@/features/vacancy/labels'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { useResource, type Resource } from '@/lib/use-resource'

export function CandidateDashboard() {
  const { state } = useAuth()
  if (state.status !== 'authenticated' || state.user.role !== 'CANDIDATE') return null
  return <Dashboard token={state.accessToken} />
}

function Dashboard({ token }: { token: string }) {
  const applications = useResource(() => applicationsApi.mine(token), `home-apps:${token}`, 'No se pudieron cargar tus procesos.')
  const agenda = useResource(() => interviewsApi.list(token, 'upcoming'), `home-agenda:${token}`, 'No se pudo cargar tu agenda.')
  const messages = useResource(() => messagesApi.inbox(token), `home-messages:${token}`, 'No se pudieron cargar tus mensajes.')
  const cv = useResource(async () => {
    const [files, consents] = await Promise.all([cvApi.list(token), cvApi.consents(token)])
    return { files, consents }
  }, `home-cv:${token}`, 'No se pudo comprobar tu CV y sus permisos.')
  const updates = useContext(ApplicationUpdatesContext)
  const status = useRealtime()?.status
  const [now, setNow] = useState(() => Date.now())
  const refreshApps = applications.refresh
  const refreshAgenda = agenda.refresh
  const refreshMessages = messages.refresh
  const refreshCv = cv.refresh
  const refreshUpdates = updates?.refresh
  const refreshAll = useCallback(() => {
    refreshApps(); refreshAgenda(); refreshMessages(); refreshCv(); refreshUpdates?.()
  }, [refreshApps, refreshAgenda, refreshMessages, refreshCv, refreshUpdates])
  useRealtimeNotices((notice) => {
    if (notice.type === 'reconnected') refreshAll()
    if (notice.type === 'application.stage.changed') refreshApps()
    if (notice.type === 'interview.changed') refreshAgenda()
    if (notice.type === 'message.sent' || notice.type === 'conversation.read') refreshMessages()
  })
  useEffect(() => {
    window.addEventListener('focus', refreshAll)
    function visible() { if (document.visibilityState === 'visible') refreshAll() }
    document.addEventListener('visibilitychange', visible)
    return () => { window.removeEventListener('focus', refreshAll); document.removeEventListener('visibilitychange', visible) }
  }, [refreshAll])
  useEffect(() => { if (status === 'online') refreshAll() }, [status, refreshAll])
  useEffect(() => {
    const timer = window.setInterval(() => { setNow(Date.now()); refreshAgenda() }, 60_000)
    return () => window.clearInterval(timer)
  }, [refreshAgenda])
  const active = applications.state.status === 'ready' ? applications.state.data.filter((a) => a.stage !== 'HIRED' && a.stage !== 'REJECTED') : []
  const upcoming = agenda.state.status === 'ready' ? agenda.state.data.items.filter((item) => ['INVITED', 'CONFIRMED'].includes(item.interview.status)
    && Date.parse(item.interview.startsAt) > now).sort((a, b) => Date.parse(a.interview.startsAt) - Date.parse(b.interview.startsAt)) : []
  const unreadMessages = messages.state.status === 'ready' ? messages.state.data.reduce((sum, item) => sum + item.unread, 0) : 0
  const unreadConversations = messages.state.status === 'ready' ? messages.state.data.filter((item) => item.unread > 0)
    .sort((a, b) => Date.parse(b.lastSentAt) - Date.parse(a.lastSentAt)) : []
  const recent = applications.state.status === 'ready' ? [...applications.state.data].sort((a, b) => Date.parse(b.appliedAt) - Date.parse(a.appliedAt) || a.id.localeCompare(b.id)).slice(0, 3) : []
  const invitation = upcoming.find((item) => item.interview.status === 'INVITED')
  const firstVisit = applications.state.status === 'ready' && applications.state.data.length === 0
    && agenda.state.status === 'ready' && upcoming.length === 0 && !agenda.state.data.hasMore
    && messages.state.status === 'ready' && messages.state.data.length === 0
    && updates?.state.status === 'ready' && updates.state.data.unread === 0 && updates.state.data.updates.length === 0
  const nextAction = invitation
    ? { title: 'Tienes una entrevista por confirmar', description: `${invitation.companyName} · ${interviewTime(invitation.interview.startsAt, invitation.interview.zoneId)}`, label: 'Revisar invitación', href: '/entrevistas' }
    : unreadMessages > 0 ? { title: 'Hay mensajes que requieren tu atención', description: 'Consulta las conversaciones pendientes con las empresas.', label: 'Leer mensajes', href: '/mensajes' }
      : cv.state.status === 'ready' && cv.state.data.files.length === 0 ? { title: 'Prepara tu CV para postular', description: 'Añade un PDF y revisa el permiso para compartirlo con las empresas a las que te postules.', label: 'Preparar mi CV', href: '/mi-cv' }
        : cv.state.status === 'ready' && !cv.state.data.consents.shareWithRecruiters ? { title: 'Revisa el permiso de tu CV', description: 'Tienes un archivo guardado. Comprueba su visibilidad antes de enviar una postulación.', label: 'Revisar permisos', href: '/mi-cv' }
          : active.length > 0 ? { title: 'Consulta tus procesos de selección', description: 'Revisa las etapas y los mensajes que cada empresa ha compartido contigo.', label: 'Consultar procesos', href: '/postulaciones' }
            : { title: 'Encuentra tu próxima oportunidad', description: 'Explora vacantes y consulta sus requisitos antes de postular.', label: 'Buscar vacantes', href: '/vacantes' }
  return <div className="app-page">
    <PageHeader title="Inicio" description="Bienvenido. Revisa tus procesos y lo que requiere tu atención." />
    <section aria-label="Acción prioritaria" className="flex flex-wrap items-center justify-between gap-4 rounded-card border border-ink-100 border-l-4 border-l-azure-600 bg-white px-4 py-3">
      <div className="min-w-0 flex-1 basis-60"><p className="text-xs font-semibold text-azure-600">Tu siguiente paso</p>
        <h2 className="mt-1 text-base font-semibold text-navy-900">{nextAction.title}</h2>
        <p className="mt-1 max-w-[65ch] break-words text-sm leading-relaxed text-ink-700">{nextAction.description}</p></div>
      <Button asChild><AppLink href={nextAction.href} className="text-white hover:text-white hover:no-underline">{nextAction.label}<ArrowRight aria-hidden="true" /></AppLink></Button>
    </section>
    <div className="grid grid-cols-2 overflow-hidden rounded-lg border border-ink-100 bg-white xl:grid-cols-4">
      <Summary title="Procesos en curso" icon={BriefcaseBusiness} state={applications.state} value={String(active.length)} href="/postulaciones" action="Ver procesos" onRetry={applications.retry} />
      <Summary title="Próximas entrevistas" icon={CalendarDays} state={agenda.state} value={`${upcoming.length}${agenda.state.status === 'ready' && agenda.state.data.hasMore ? '+' : ''}`} href="/entrevistas" action="Ver agenda" onRetry={agenda.retry} />
      <Summary title="Mensajes sin leer" icon={MessageSquare} state={messages.state} value={String(unreadMessages)} href="/mensajes" action="Ver mensajes" onRetry={messages.retry} />
      <Summary title="Archivos de CV" icon={FileText} state={cv.state} value={`${cv.state.status === 'ready' ? cv.state.data.files.length : '—'} / ${MAX_CVS}`} href="/mi-cv" action="Gestionar CV" onRetry={cv.retry} />
    </div>
    {firstVisit ? <SectionCard title="Comienza tu búsqueda">
      <ol className="grid gap-5 md:grid-cols-3">
        <li className="min-w-0"><p className="text-xs font-semibold text-azure-600">Paso 1</p><h3 className="mt-1 text-sm font-semibold">Prepara tus archivos</h3>
          <ResourceMessage state={cv.state} onRetry={cv.retry} />
          {cv.state.status === 'ready' && <p className="mt-2 text-sm leading-relaxed text-ink-700">{cv.state.data.files.length === 0 ? 'Sube un CV en PDF. Después podrás elegirlo al postular.' : cv.state.data.consents.shareWithRecruiters ? 'Ya tienes un CV y permiso para compartirlo. Revisa que sus datos estén actualizados.' : 'Tu CV está guardado. Revisa el permiso para compartirlo antes de postular.'}</p>}
          <AppLink href="/mi-cv" className="mt-2 inline-flex min-h-8 items-center text-sm">Gestionar mi CV</AppLink></li>
        <li className="min-w-0"><p className="text-xs font-semibold text-azure-600">Paso 2</p><h3 className="mt-1 text-sm font-semibold">Encuentra una oportunidad</h3>
          <p className="mt-2 text-sm leading-relaxed text-ink-700">Busca por puesto, empresa o ubicación y consulta los requisitos de cada vacante.</p><AppLink href="/vacantes" className="mt-2 inline-flex min-h-8 items-center text-sm">Explorar vacantes</AppLink></li>
        <li className="min-w-0"><p className="text-xs font-semibold text-azure-600">Paso 3</p><h3 className="mt-1 text-sm font-semibold">Sigue tus procesos</h3>
          <p className="mt-2 text-sm leading-relaxed text-ink-700">Cuando envíes una postulación, podrás consultar sus etapas y los avisos de la empresa.</p><AppLink href="/postulaciones" className="mt-2 inline-flex min-h-8 items-center text-sm">Ir a mis postulaciones</AppLink></li>
      </ol>
    </SectionCard> : <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
      <div className="flex min-w-0 flex-col gap-4">
        <Panel title="Tu agenda" href="/entrevistas" action="Ver todas las entrevistas">
          <ResourceMessage state={agenda.state} onRetry={agenda.retry} />
          {agenda.state.status === 'ready' && (upcoming.length === 0 ? <p className="text-sm leading-relaxed text-ink-700">No tienes entrevistas próximas en esta consulta. Las invitaciones aparecerán aquí cuando una empresa las agende.</p>
            : <><ol className="divide-y divide-ink-100">{upcoming.slice(0, 3).map(({ interview: i, vacancyTitle, companyName }) => <li key={i.id} className="py-3 first:pt-0 last:pb-0">
              <div className="flex flex-wrap items-start justify-between gap-2"><p className="break-words text-sm font-semibold">{i.title}</p><Badge tone={i.status === 'INVITED' ? 'mid' : 'high'}>{i.status === 'INVITED' ? 'Por confirmar' : 'Confirmada'}</Badge></div>
              <p className="mt-1 break-words text-sm text-ink-700">{companyName} · {vacancyTitle}</p>
              <p className="mt-2 text-sm font-medium text-navy-900"><time dateTime={i.startsAt}>{interviewTime(i.startsAt, i.zoneId)}</time></p>
              <p className="mt-1 text-xs text-ink-500">{i.zoneId} · {i.durationMinutes} min</p>
              <AppLink href="/entrevistas" className="mt-1 inline-flex min-h-8 items-center text-sm">{i.status === 'INVITED' ? 'Revisar invitación y responder' : 'Consultar detalles de la cita'}</AppLink>
            </li>)}</ol>
              {(upcoming.length > 3 || agenda.state.data.hasMore) && <p className="mt-4 border-t border-ink-100 pt-3 text-xs leading-5 text-ink-500">Aquí aparecen las tres primeras citas disponibles. Consulta la agenda para ver el resto.</p>}
            </>)}
        </Panel>
        <Panel title="Postulaciones recientes" href="/postulaciones" action="Ver todas las postulaciones">
          <ResourceMessage state={applications.state} onRetry={applications.retry} />
          {applications.state.status === 'ready' && (recent.length === 0 ? <div className="text-sm leading-relaxed text-ink-700"><p>Aún no has enviado postulaciones.</p><AppLink href="/vacantes" className="mt-2 inline-flex min-h-8 items-center">Buscar una vacante</AppLink></div>
            : <ul className="divide-y divide-ink-100">{recent.map((a) => <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-3 first:pt-0 last:pb-0">
              <div className="min-w-0 flex-1 basis-48"><p className="break-words text-sm font-semibold">{a.vacancyTitle}</p><p className="mt-1 break-words text-sm text-ink-700">{a.companyName}</p>
                <p className="mt-1 text-xs text-ink-500">Enviada el <time dateTime={a.appliedAt}>{formatDate(a.appliedAt)}</time></p></div>
              <div className="flex flex-wrap items-center gap-2"><Badge tone={a.stage === 'HIRED' ? 'high' : a.stage === 'REJECTED' ? 'neutral' : 'azure'}>{stageLabel[a.stage ?? 'RECEIVED']}</Badge>
                {updates?.state.status === 'ready' && updates.state.data.updates.some((u) => u.applicationId === a.id) && <span className="text-xs font-medium text-azure-600">Cambio sin leer</span>}</div>
            </li>)}</ul>)}
        </Panel>
      </div>
      <div className="flex min-w-0 flex-col gap-4">
        <Panel title="Mensajes pendientes" href="/mensajes" action="Abrir bandeja">
          <ResourceMessage state={messages.state} onRetry={messages.retry} />
          {messages.state.status === 'ready' && (unreadConversations.length === 0 ? <p className="text-sm text-ink-700">No tienes mensajes sin leer.</p>
            : <ul className="divide-y divide-ink-100">{unreadConversations.slice(0, 3).map((item) => <li key={item.applicationId} className="py-3 first:pt-0 last:pb-0">
              <div className="flex items-start justify-between gap-2"><p className="min-w-0 break-words text-sm font-semibold">{item.companyName}</p><Badge tone="azure">{item.unread} sin leer</Badge></div>
              <p className="mt-1 break-words text-sm text-ink-700">{item.vacancyTitle}</p>
              <AppLink href={`/mensajes/${item.applicationId}`} className="mt-1 inline-flex min-h-8 items-center text-sm">Abrir conversación</AppLink>
            </li>)}</ul>)}
        </Panel>
        <Panel title="Novedades de tus procesos" href="/postulaciones" action="Ver postulaciones">
          {!updates ? <p className="text-sm text-ink-700">Consulta tus postulaciones para revisar el seguimiento.</p> : <>
            <ResourceMessage state={updates.state} onRetry={updates.refresh} />
            {updates.state.status === 'ready' && (updates.state.data.updates.length === 0 ? <p className="text-sm text-ink-700">No tienes cambios de etapa sin leer.</p>
              : <ul className="divide-y divide-ink-100">{updates.state.data.updates.slice(0, 3).map((u) => <li key={u.applicationId} className="py-3 first:pt-0 last:pb-0">
                <p className="break-words text-sm font-semibold">{u.vacancyTitle}</p><p className="mt-1 text-sm text-ink-700">{u.companyName}</p>
                <div className="mt-2 flex flex-wrap items-center gap-2"><Badge tone="azure">{stageLabel[u.stage]}</Badge><time dateTime={u.changedAt} className="text-xs text-ink-500">{formatDate(u.changedAt)}</time></div>
              </li>)}</ul>)}
          </>}
        </Panel>
        <Panel title="Prepara tu próxima postulación" href="/mi-cv" action="Revisar mi CV">
          <ResourceMessage state={cv.state} onRetry={cv.retry} />
          {cv.state.status === 'ready' && <>
            <Badge tone={cv.state.data.files.length > 0 && cv.state.data.consents.shareWithRecruiters ? 'high' : 'mid'}>
              {cv.state.data.files.length === 0 ? 'Falta subir un CV' : cv.state.data.consents.shareWithRecruiters ? 'CV y permiso disponibles' : 'Revisa el permiso para compartir'}
            </Badge>
            <p className="mt-2 max-w-prose text-sm leading-relaxed text-ink-700">{cv.state.data.files.length === 0 ? 'Sube un PDF para poder elegirlo al postular. Puedes preparar tu CV mientras exploras vacantes.'
              : cv.state.data.consents.shareWithRecruiters ? 'Puedes elegir uno de tus archivos al postular. Revisa que sus datos estén actualizados.'
                : 'Tienes un CV guardado. Antes de postular, permite compartirlo con las empresas a las que envíes tu candidatura.'}</p>
          </>}
          <Button asChild variant="secondary" size="sm" className="mt-3"><AppLink href="/vacantes">Explorar vacantes</AppLink></Button>
        </Panel>
      </div>
    </div>}
  </div>
}

function Summary({ title, icon: Icon, state, value, href, action, onRetry }: { title: string; icon: LucideIcon; state: Resource<unknown>; value: string; href: string; action: string; onRetry: () => void }) {
  return <section aria-label={title} className="min-w-0 border-r border-b border-ink-100 px-4 py-3 even:border-r-0 [&:nth-last-child(-n+2)]:border-b-0 xl:border-b-0 xl:even:border-r xl:last:border-r-0">
    <div className="flex items-start justify-between gap-2"><h2 className="min-h-8 text-xs font-medium text-ink-700 sm:min-h-0 sm:text-sm">{title}</h2><Icon className="size-4 shrink-0 text-ink-500" aria-hidden="true" /></div>
    <p className="mt-1 text-2xl font-semibold text-navy-900 tabular-nums">{state.status === 'loading' ? '…' : state.status === 'error' ? '—' : value}</p>
    {state.status === 'error' && <p className="mt-1 text-xs text-ink-500">Datos no disponibles. <button className="cursor-pointer underline" onClick={onRetry}>Reintentar</button></p>}
    <AppLink href={href} className="mt-1 inline-flex min-h-8 items-center text-xs">{action}</AppLink>
  </section>
}

function ResourceMessage({ state, onRetry }: { state: Resource<unknown>; onRetry: () => void }) {
  if (state.status === 'loading') return <p role="status" className="text-sm text-ink-500">Cargando información…</p>
  if (state.status === 'error') return <p role="status" className="text-sm leading-relaxed text-ink-700">{state.message} <button className="cursor-pointer font-medium underline" onClick={onRetry}>Volver a consultar</button></p>
  return null
}

function Panel({ title, href, action, children }: { title: string; href: string; action: string; children: React.ReactNode }) {
  return <SectionCard title={title} action={<AppLink href={href} className="inline-flex min-h-8 items-center text-xs">{action}</AppLink>}>{children}</SectionCard>
}
