import { useCallback, useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { AppLink } from '@/components/app-link'
import { PageHeader } from '@/components/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { SectionCard } from '@/components/ui/section-card'
import { useAuth } from '@/features/auth/auth-context'
import { interviewsApi } from '@/features/interviews/api'
import { interviewTime } from '@/features/interviews/interview-time'
import { messagesApi } from '@/features/messages/api'
import { companyVacancies } from '@/features/vacancy/api'
import { formatDate, statusLabel, statusTone } from '@/features/vacancy/labels'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { useResource } from '@/lib/use-resource'

export function CompanyDashboard() {
  const { state: auth } = useAuth()
  if (auth.status !== 'authenticated' || !['RECRUITER', 'COMPANY_ADMIN'].includes(auth.user.role)) return null
  return <Dashboard token={auth.accessToken} />
}

function Dashboard({ token }: { token: string }) {
  const vacancies = useResource(() => companyVacancies.list(token), `inicio-empresa-vacantes:${token}`, 'No se pudieron cargar las vacantes.')
  const agenda = useResource(() => interviewsApi.list(token, 'upcoming'), `inicio-empresa-agenda:${token}`, 'No se pudo cargar la agenda.')
  const messages = useResource(() => messagesApi.inbox(token), `inicio-empresa-mensajes:${token}`, 'No se pudieron cargar los mensajes.')
  const { refresh: refreshVacancies } = vacancies
  const { refresh: refreshAgenda } = agenda
  const { refresh: refreshMessages } = messages
  const { status } = useRealtime() ?? {}
  const [now, setNow] = useState(() => Date.now())
  const refreshAll = useCallback(() => { refreshVacancies(); refreshAgenda(); refreshMessages() }, [refreshVacancies, refreshAgenda, refreshMessages])
  useRealtimeNotices((notice) => {
    if (notice.type === 'reconnected') refreshAll()
    if (notice.type === 'interview.changed') refreshAgenda()
    if (notice.type === 'message.sent' || notice.type === 'conversation.read') refreshMessages()
  })
  useEffect(() => {
    const visible = () => { if (document.visibilityState === 'visible') refreshAll() }
    window.addEventListener('focus', refreshAll)
    document.addEventListener('visibilitychange', visible)
    return () => { window.removeEventListener('focus', refreshAll); document.removeEventListener('visibilitychange', visible) }
  }, [refreshAll])
  useEffect(() => { if (status === 'online') refreshAll() }, [status, refreshAll])
  useEffect(() => {
    const timer = window.setInterval(() => { setNow(Date.now()); refreshAgenda() }, 60_000)
    return () => window.clearInterval(timer)
  }, [refreshAgenda])

  const all = vacancies.state.status === 'ready' ? vacancies.state.data : []
  const recent = [...all].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt)).slice(0, 4)
  const upcoming = agenda.state.status === 'ready' ? agenda.state.data.items.filter((item) => ['INVITED', 'CONFIRMED'].includes(item.interview.status)
    && Date.parse(item.interview.startsAt) > now).sort((a, b) => Date.parse(a.interview.startsAt) - Date.parse(b.interview.startsAt)).slice(0, 3) : []
  const unread = messages.state.status === 'ready' ? messages.state.data.filter((c) => c.unread > 0).sort((a, b) => Date.parse(b.lastSentAt) - Date.parse(a.lastSentAt)) : []
  const stats = [
    { label: 'Vacantes publicadas', value: vacancies.state.status === 'ready' ? all.filter((v) => v.status === 'PUBLISHED').length : null, href: '/vacantes' },
    { label: 'Borradores', value: vacancies.state.status === 'ready' ? all.filter((v) => v.status === 'DRAFT').length : null, href: '/vacantes' },
    { label: 'Vacantes cerradas', value: vacancies.state.status === 'ready' ? all.filter((v) => v.status === 'CLOSED').length : null, href: '/vacantes' },
    { label: 'Mensajes sin leer', value: messages.state.status === 'ready' ? unread.reduce((sum, c) => sum + c.unread, 0) : null, href: '/mensajes' },
  ]
  return <div className="app-page">
    <PageHeader title="Inicio de empresa" description="Organiza las vacantes, las entrevistas y las conversaciones de tu equipo."
      actions={<Button asChild><AppLink href="/vacantes/nueva"><Plus aria-hidden="true" />Nueva vacante</AppLink></Button>} />
    <div className="grid grid-cols-2 overflow-hidden rounded-card border border-ink-100 bg-white sm:grid-cols-4">
      {stats.map((stat) => <section key={stat.label} aria-label={stat.label} className="min-w-0 border-r border-b border-ink-100 p-4">
        <AppLink href={stat.href} className="inline-flex min-h-6 text-xs font-medium text-ink-700">{stat.label}</AppLink>
        <p className="mt-1 text-2xl font-semibold tabular-nums text-navy-900">{stat.value ?? '—'}</p>
        {stat.value === null && <p className="text-xs text-ink-500">Pendiente de consulta</p>}
      </section>)}
    </div>
    <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
      <div className="min-w-0 space-y-4">
        <SectionCard title="Vacantes recientes" action={<AppLink href="/vacantes" className="inline-flex min-h-8 items-center text-xs">Ver todas</AppLink>}>
          {vacancies.state.status === 'loading' && <LoadingRows rows={2} />}
          {vacancies.state.status === 'error' && <LoadError title="No se pudieron cargar las vacantes" message={vacancies.state.message} onRetry={vacancies.retry} />}
          {vacancies.state.status === 'ready' && (recent.length === 0 ? <div className="space-y-2 text-sm text-ink-700"><p className="font-semibold">Comienza con tu primera vacante</p><p>Describe el puesto y configura los criterios internos de revisión. Puedes guardar un borrador antes de publicar.</p><AppLink href="/vacantes/nueva" className="inline-flex min-h-8 items-center">Crear vacante</AppLink></div>
            : <ul aria-label="Vacantes recientes" className="divide-y divide-ink-100">{recent.map((v) => <li key={v.id} className="flex min-w-0 flex-col items-start justify-between gap-2 py-3 first:pt-0 last:pb-0 sm:flex-row">
              <div className="min-w-0 flex-1"><AppLink href={`/vacantes/${v.id}`} className="inline-flex min-h-6 break-words text-sm">{v.title}</AppLink><p className="mt-1 text-xs text-ink-500">Editada el {formatDate(v.updatedAt)}</p></div>
              <Badge tone={statusTone[v.status]}>{statusLabel[v.status]}</Badge>
            </li>)}</ul>)}
        </SectionCard>
        <SectionCard title="Próximas entrevistas" action={<AppLink href="/entrevistas" className="inline-flex min-h-8 items-center text-xs">Ver agenda</AppLink>}>
          {agenda.state.status === 'loading' && <LoadingRows rows={2} />}
          {agenda.state.status === 'error' && <LoadError title="No se pudo cargar la agenda" message={agenda.state.message} onRetry={agenda.retry} />}
          {agenda.state.status === 'ready' && <>
            {upcoming.length === 0 ? <p className="text-sm text-ink-700">No hay entrevistas próximas en esta página de la agenda. Puedes enviar invitaciones desde las postulaciones de una vacante.</p>
              : <ul className="divide-y divide-ink-100" aria-label="Agenda próxima">{upcoming.map(({interview: i, vacancyTitle}) => <li key={i.id} className="space-y-1 py-3 first:pt-0 last:pb-0">
                <p className="break-words text-sm font-semibold">{i.title}</p><p className="break-words text-xs text-ink-700">{vacancyTitle}</p>
                <p className="text-xs leading-5 text-ink-700">{interviewTime(i.startsAt, i.zoneId)} · {i.durationMinutes} minutos</p>
                <Badge tone={i.status === 'CONFIRMED' ? 'high' : 'azure'}>{i.status === 'CONFIRMED' ? 'Confirmada' : 'Pendiente de confirmar'}</Badge>
              </li>)}</ul>}
            <p className="mt-3 text-xs leading-5 text-ink-500">Se muestran hasta 3 entrevistas de la primera página.{agenda.state.data.hasMore ? ' Hay más resultados en la agenda.' : ''} Abre la agenda para revisar los detalles y cambios.</p>
          </>}
        </SectionCard>
      </div>
      <div className="min-w-0 space-y-4">
        <SectionCard title="Conversaciones pendientes" action={<AppLink href="/mensajes" className="inline-flex min-h-8 items-center text-xs">Ver mensajes</AppLink>}>
          {messages.state.status === 'loading' && <LoadingRows rows={2} />}
          {messages.state.status === 'error' && <LoadError title="No se pudieron cargar los mensajes" message={messages.state.message} onRetry={messages.retry} />}
          {messages.state.status === 'ready' && (unread.length === 0 ? <p className="text-sm text-ink-700">No tienes mensajes sin leer.</p> : <>
            <ul aria-label="Conversaciones sin leer" className="divide-y divide-ink-100">{unread.slice(0, 3).map((c) => <li key={c.applicationId} className="py-3 first:pt-0 last:pb-0">
              <AppLink href={`/mensajes/${c.applicationId}`} className="inline-flex min-h-6 break-words text-sm">{c.candidateEmail ?? 'Persona candidata'}</AppLink>
              <p className="break-words text-xs text-ink-700">{c.vacancyTitle}</p><p className="mt-1 text-xs text-ink-500">{c.unread} sin leer</p>
            </li>)}</ul>{unread.length > 3 && <p className="mt-3 text-xs text-ink-500">Hay más conversaciones pendientes en Mensajes.</p>}
          </>)}
        </SectionCard>
        <SectionCard title="Tu siguiente paso">
          <ul className="space-y-3 text-sm text-ink-700"><li><AppLink href="/vacantes" className="inline-flex min-h-6">Revisar postulaciones</AppLink><p className="text-xs leading-5">Abre una vacante para consultar CV, solicitar análisis y actualizar cada proceso.</p></li>
            <li><AppLink href="/equipo" className="inline-flex min-h-6">Consultar el equipo</AppLink><p className="text-xs leading-5">La administración de la empresa puede invitar a otras personas a reclutar.</p></li></ul>
        </SectionCard>
      </div>
    </div>
  </div>
}
