import { ArrowLeft, CalendarClock, EyeOff, FileText, LoaderCircle, MessageSquare, SearchX, Users, Video } from 'lucide-react'
import { useEffect, useState } from 'react'
import { AppLink } from '@/components/app-link'
import { PageHeader } from '@/components/page-header'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { ApiError } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { applicationsApi, type ApplicationStage } from '@/features/application/api'
import { ApplicationProgressPanel } from '@/features/application/application-progress'
import { screeningFailureMessage } from '@/features/application/screening-failure'
import { screeningLabel } from '@/features/application/screening-labels'
import { stageLabel } from '@/features/application/stage-labels'
import { companyVacancies } from '@/features/vacancy/api'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { cn } from '@/lib/cn'
import { errorMessage } from '@/lib/http'
import { useRealtimeNotices } from '@/lib/realtime-context'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { candidateLabel, companyApplicationsApi, type CandidateFilters, type CompanyApplication } from './api'

const day = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short', year: 'numeric' })
const dayTime = new Intl.DateTimeFormat('es', { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
const stages = Object.keys(stageLabel) as ApplicationStage[]
const stageTone: Record<ApplicationStage, 'neutral' | 'azure' | 'high' | 'mid' | 'low'> = {
  RECEIVED: 'neutral', REVIEW: 'azure', INTERVIEW: 'azure', OFFER: 'mid', HIRED: 'high', REJECTED: 'low',
}

/** VALOR SUPUESTO: espera tras la última tecla antes de buscar. */
const SEARCH_DELAY_MS = 300

type ListState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; items: CompanyApplication[]; total: number; page: number; loadingMore: boolean }

/**
 * Candidatos: todas las postulaciones de las vacantes de la empresa.
 * Escritorio: lista a la izquierda y detalle a la derecha. Móvil: una cosa a la vez.
 * Nunca muestra la puntuación del modelo: eso se compara solo dentro del ranking de cada vacante.
 */
export function CandidatesPage({ applicationId }: { applicationId?: string }) {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const [text, setText] = useState('')
  const [filters, setFilters] = useState<CandidateFilters>({})
  const [list, setList] = useState<ListState>({ status: 'loading' })
  const [version, setVersion] = useState(0)
  const vacancies = useResource(() => companyVacancies.list(token), `vacantes-filtro:${token}`, '')

  // La búsqueda espera a que se deje de escribir.
  useEffect(() => {
    const timer = window.setTimeout(() => setFilters((f) => (f.q === text ? f : { ...f, q: text })), SEARCH_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [text])

  useEffect(() => {
    let active = true
    companyApplicationsApi.search(token, filters).then(
      (page) => active && setList({ status: 'ready', items: page.items, total: page.total, page: 0, loadingMore: false }),
      (err: unknown) => active && setList({ status: 'error', message: errorMessage(err, 'No se pudieron cargar los candidatos.') }),
    )
    return () => {
      active = false
    }
  }, [token, filters, version])

  // Una etapa o una entrevista cambiada en otra pestaña o por otra persona del equipo.
  useRealtimeNotices((notice) => {
    if (notice.type === 'application.stage.changed' || notice.type === 'interview.changed' || notice.type === 'reconnected') {
      setVersion((v) => v + 1)
    }
  })

  async function loadMore() {
    if (list.status !== 'ready') return
    setList({ ...list, loadingMore: true })
    try {
      const next = await companyApplicationsApi.search(token, filters, list.page + 1)
      setList((current) => current.status === 'ready'
        ? { status: 'ready', items: [...current.items, ...next.items], total: next.total, page: next.page, loadingMore: false }
        : current)
    } catch {
      setList((current) => (current.status === 'ready' ? { ...current, loadingMore: false } : current))
    }
  }

  const filtering = Boolean(filters.vacancyId || filters.stage || filters.q?.trim())
  const vacancyOptions = vacancies.state.status === 'ready' ? vacancies.state.data.filter((v) => v.status !== 'DRAFT') : []

  return (
    <div className="app-page">
      <div className={cn(applicationId && 'hidden lg:block')}>
        <PageHeader
          title="Candidatos"
          description="Todas las personas que se postularon a tus vacantes. El orden y la decisión final siempre son de tu equipo."
        />
      </div>

      <div className="lg:grid lg:grid-cols-[minmax(0,26rem)_minmax(0,1fr)] lg:items-start lg:gap-4">
        <section aria-label="Lista de candidatos" className={cn('flex min-w-0 flex-col gap-3', applicationId && 'hidden lg:flex')}>
          <div role="search" aria-label="Buscar y filtrar candidatos" className="grid gap-3 rounded-card border border-ink-100 bg-white p-4 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            <div className="sm:col-span-2 lg:col-span-1 xl:col-span-2">
              <Field id="buscar-candidatos" label="Nombre, correo o vacante">
                <Input id="buscar-candidatos" type="search" value={text} maxLength={100} placeholder="Buscar"
                  onChange={(e) => setText(e.target.value)} />
              </Field>
            </div>
            <Field id="filtro-vacante" label="Vacante">
              <select id="filtro-vacante" value={filters.vacancyId ?? ''}
                onChange={(e) => setFilters((f) => ({ ...f, vacancyId: e.target.value || undefined }))}
                className="h-10 w-full min-w-0 rounded-md border border-ink-100 bg-white px-3 text-base sm:text-sm">
                <option value="">Todas las vacantes</option>
                {vacancyOptions.map((v) => <option key={v.id} value={v.id}>{v.title}</option>)}
              </select>
            </Field>
            <Field id="filtro-etapa" label="Etapa">
              <select id="filtro-etapa" value={filters.stage ?? ''}
                onChange={(e) => setFilters((f) => ({ ...f, stage: (e.target.value || undefined) as ApplicationStage | undefined }))}
                className="h-10 w-full min-w-0 rounded-md border border-ink-100 bg-white px-3 text-base sm:text-sm">
                <option value="">Todas las etapas</option>
                {stages.map((s) => <option key={s} value={s}>{stageLabel[s]}</option>)}
              </select>
            </Field>
          </div>

          {list.status === 'loading' && <LoadingRows rows={3} />}
          {list.status === 'error' && (
            <LoadError title="No se pudieron cargar los candidatos" message={list.message} onRetry={() => setVersion((v) => v + 1)} />
          )}
          {list.status === 'ready' && (
            <>
              <p role="status" className="text-sm text-ink-700">
                {list.total === 1 ? '1 postulación' : `${list.total} postulaciones`}
                {filtering && ' con estos filtros'}
              </p>
              {list.items.length === 0 ? (
                filtering ? (
                  <StateMessage icon={SearchX} title="Nadie coincide con estos filtros"
                    description="Prueba con otro nombre, otra vacante u otra etapa."
                    action={<Button variant="secondary" onClick={() => { setText(''); setFilters({}) }}>Limpiar filtros</Button>} />
                ) : (
                  <StateMessage icon={Users} title="Todavía no hay postulaciones"
                    description="Cuando alguien se postule a una de tus vacantes publicadas, aparecerá aquí."
                    action={<Button onClick={() => navigate('/vacantes')}>Ver vacantes</Button>} />
                )
              ) : (
                <ul className="divide-y divide-ink-100 overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft">
                  {list.items.map((a) => (
                    <li key={a.applicationId}>
                      <CandidateRow application={a} current={a.applicationId === applicationId} />
                    </li>
                  ))}
                </ul>
              )}
              {list.items.length < list.total && (
                <Button variant="secondary" className="self-center" onClick={() => void loadMore()} disabled={list.loadingMore}>
                  {list.loadingMore && <LoaderCircle className="animate-spin" aria-hidden="true" />}
                  Ver más ({list.total - list.items.length} restantes)
                </Button>
              )}
            </>
          )}
        </section>

        <section aria-label="Detalle del candidato" className={cn('min-w-0', !applicationId && 'hidden lg:block')}>
          {applicationId ? (
            <CandidateDetail key={applicationId} token={token} applicationId={applicationId}
              onChanged={() => setVersion((v) => v + 1)} />
          ) : (
            <StateMessage icon={Users} title="Elige una persona" description="Verás su CV, la etapa, las entrevistas y los mensajes en un solo lugar." className="py-16" />
          )}
        </section>
      </div>
    </div>
  )
}

function CandidateRow({ application: a, current }: { application: CompanyApplication; current: boolean }) {
  const screening = a.screeningStatus ? screeningLabel[a.screeningStatus] : null
  return (
    <AppLink
      href={`/candidatos/${a.applicationId}`}
      aria-current={current ? 'page' : undefined}
      className={cn(
        'flex flex-col gap-1.5 rounded-none px-4 py-3 font-normal text-ink-900 no-underline hover:bg-azure-50 hover:text-ink-900 hover:no-underline',
        // Fondo claro y barra lateral: las etiquetas azules siguen viéndose sobre la fila elegida.
        current && 'bg-azure-50 shadow-[inset_3px_0_0_var(--color-azure-600)]',
      )}
    >
      <span className="flex items-start gap-3">
        <span className={cn('min-w-0 flex-1 truncate text-sm font-semibold', !a.shared && 'inline-flex items-center gap-1.5 text-ink-700')}>
          {!a.shared && <EyeOff className="size-4 shrink-0 text-ink-500" aria-hidden="true" />}
          {candidateLabel(a)}
        </span>
        {a.stage && <Badge tone={stageTone[a.stage]}>{stageLabel[a.stage]}</Badge>}
      </span>
      <span className="truncate text-xs text-ink-500">{a.vacancyTitle} · {day.format(new Date(a.appliedAt))}</span>
      {(screening || a.nextInterviewAt) && (
        <span className="flex flex-wrap items-center gap-2 text-xs">
          {screening && <Badge tone={screening.tone}>{screening.text}</Badge>}
          {a.nextInterviewAt && (
            <span className="inline-flex items-center gap-1 text-ink-700">
              <CalendarClock className="size-3.5" aria-hidden="true" />
              Entrevista: {dayTime.format(new Date(a.nextInterviewAt))}
            </span>
          )}
        </span>
      )}
    </AppLink>
  )
}

type DetailState =
  | { status: 'loading' }
  | { status: 'missing' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: CompanyApplication }

function CandidateDetail({ token, applicationId, onChanged }: { token: string; applicationId: string; onChanged: () => void }) {
  const [state, setState] = useState<DetailState>({ status: 'loading' })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    companyApplicationsApi.find(token, applicationId).then(
      (data) => active && setState({ status: 'ready', data }),
      (err: unknown) => active && setState(err instanceof ApiError && err.status === 404
        ? { status: 'missing' }
        : { status: 'error', message: errorMessage(err, 'No se pudo cargar la postulación.') }),
    )
    return () => {
      active = false
    }
  }, [token, applicationId, version])

  useRealtimeNotices((notice) => {
    const relevant = notice.type === 'reconnected'
      || ((notice.type === 'application.stage.changed' || notice.type === 'interview.changed') && notice.applicationId === applicationId)
    if (relevant) setVersion((v) => v + 1)
  })

  const back = (
    <AppLink href="/candidatos" className="inline-flex items-center gap-1.5 text-sm lg:hidden">
      <ArrowLeft className="size-4" aria-hidden="true" />
      Volver a candidatos
    </AppLink>
  )

  if (state.status === 'loading') return <div className="flex flex-col gap-3">{back}<LoadingRows rows={2} /></div>
  if (state.status === 'error') {
    return <div className="flex flex-col gap-3">{back}
      <LoadError title="No se pudo cargar la postulación" message={state.message} onRetry={() => setVersion((v) => v + 1)} /></div>
  }
  if (state.status === 'missing') {
    return <div className="flex flex-col gap-3">{back}
      <StateMessage icon={SearchX} title="Esta postulación no existe" description="Puede que la persona la haya retirado."
        action={<Button onClick={() => navigate('/candidatos')}>Ver candidatos</Button>} /></div>
  }

  const a = state.data
  const screening = a.screeningStatus ? screeningLabel[a.screeningStatus] : null
  return (
    <article aria-labelledby="candidato-titulo" className="flex flex-col gap-4">
      {back}
      <div className="rounded-card border border-ink-100 bg-white p-4 shadow-soft sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 id="candidato-titulo" className="break-words text-xl font-semibold text-navy-900">{candidateLabel(a)}</h2>
            {a.shared && a.candidateName && a.candidateEmail && (
              <p className="mt-0.5 break-all text-sm text-ink-700">{a.candidateEmail}</p>
            )}
            <p className="mt-1 text-sm text-ink-500">
              Se postuló a <AppLink href={`/vacantes/${a.vacancyId}`}>{a.vacancyTitle}</AppLink> el {day.format(new Date(a.appliedAt))}
            </p>
          </div>
          {a.stage && <Badge tone={stageTone[a.stage]}>{stageLabel[a.stage]}</Badge>}
        </div>

        {!a.shared ? (
          <p className="mt-4 flex gap-2 rounded-lg bg-azure-50 px-4 py-3 text-sm leading-relaxed text-ink-700">
            <EyeOff className="mt-0.5 size-4 shrink-0 text-ink-500" aria-hidden="true" />
            La persona retiró el permiso de compartir sus datos. La postulación sigue contando, pero no puedes ver su
            CV, cambiar su etapa ni escribirle.
          </p>
        ) : (
          <>
            <div className="mt-4 flex flex-wrap gap-2">
              <OpenCv token={token} vacancyId={a.vacancyId} applicationId={a.applicationId} name={candidateLabel(a)} />
              <Button variant="secondary" size="sm" onClick={() => navigate(`/mensajes/${a.applicationId}`)}>
                <MessageSquare aria-hidden="true" />
                Mensaje
              </Button>
              <Button variant="secondary" size="sm" onClick={() => navigate(`/entrevistas?postulacion=${a.applicationId}`)}>
                <Video aria-hidden="true" />
                Programar entrevista
              </Button>
            </div>
            <dl className="mt-4 grid gap-3 border-t border-ink-100 pt-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-ink-500">Análisis automático</dt>
                <dd className="mt-1">
                  {screening ? <Badge tone={screening.tone}>{screening.text}</Badge> : <span className="text-ink-700">Sin pedir</span>}
                </dd>
              </div>
              <div>
                <dt className="text-ink-500">Próxima entrevista</dt>
                <dd className="mt-1 text-ink-900">
                  {a.nextInterviewAt ? dayTime.format(new Date(a.nextInterviewAt)) : <span className="text-ink-700">Ninguna programada</span>}
                </dd>
              </div>
            </dl>
            {a.screeningStatus === 'FAILED' && (
              <p className="mt-3 text-sm leading-relaxed text-ink-700">{screeningFailureMessage(a.screeningFailureCode)}</p>
            )}
            <p className="mt-3 text-xs text-ink-500">
              La puntuación del modelo se ve solo en el <AppLink href={`/vacantes/${a.vacancyId}/ranking`}>ranking de la vacante</AppLink>,
              donde todas las personas se comparan con los mismos criterios.
            </p>
          </>
        )}
      </div>

      {a.shared && (
        <div className="rounded-card border border-ink-100 bg-white pt-4 shadow-soft">
          <ApplicationProgressPanel token={token} applicationId={a.applicationId} vacancyId={a.vacancyId}
            label={candidateLabel(a)} onStageChanged={() => { setVersion((v) => v + 1); onChanged() }} />
        </div>
      )}
    </article>
  )
}

/**
 * Abre el CV en otra pestaña con un enlace temporal. La pestaña se abre en el mismo clic (si se abriera
 * después de esperar a la API, el navegador la bloquearía como ventana emergente).
 */
function OpenCv({ token, vacancyId, applicationId, name }: { token: string; vacancyId: string; applicationId: string; name: string }) {
  const [opening, setOpening] = useState(false)
  const [error, setError] = useState('')

  async function open() {
    setOpening(true)
    setError('')
    const tab = window.open('', '_blank')
    try {
      const { url } = await applicationsApi.cvLink(token, vacancyId, applicationId)
      if (tab) {
        tab.opener = null
        tab.location.href = url
      } else {
        window.location.assign(url)
      }
    } catch (err) {
      tab?.close()
      setError(errorMessage(err, 'No se pudo abrir el CV. Vuelve a intentarlo.'))
    } finally {
      setOpening(false)
    }
  }

  return (
    <>
      <Button size="sm" onClick={() => void open()} disabled={opening} aria-label={`Ver el CV de ${name} (se abre en otra pestaña)`}>
        {opening ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <FileText aria-hidden="true" />}
        Ver CV
      </Button>
      {error && <Notice tone="error" className="basis-full">{error}</Notice>}
    </>
  )
}
