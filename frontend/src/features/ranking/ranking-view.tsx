import { Building2, CalendarDays, Cpu, FileSearch, Inbox, MapPin, RefreshCw, ServerCrash, UserCheck } from 'lucide-react'
import { useMemo, useState } from 'react'
import { AppLink } from '@/components/app-link'
import { CandidateRow } from '@/components/candidate-row'
import { FilterBar } from '@/components/filter-bar'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { decisionState, filterLabel } from './labels'
import type { Decision, DecisionFilter, RankedCandidate, Vacancy } from './types'
import type { RankingState } from './use-vacancy-ranking'

type RankingViewProps = {
  state: RankingState
  onRetry: () => void
  onDecide: (candidateId: string, decision: Decision | null) => void
}

const filters: DecisionFilter[] = ['todos', 'sin_revisar', 'preseleccionado', 'en_espera', 'descartado']

function matches(c: RankedCandidate, f: DecisionFilter) {
  if (f === 'todos') return true
  if (f === 'sin_revisar') return c.decision === null
  return c.decision === f
}

/** Pantalla de referencia: ranking de CV de una vacante con decisión humana. */
export function RankingView({ state, onRetry, onDecide }: RankingViewProps) {
  const [announcement, setAnnouncement] = useState('')

  function handleDecide(c: RankedCandidate, d: Decision | null) {
    onDecide(c.id, d)
    setAnnouncement(`${c.fullName}: ${d ? decisionState[d].toLowerCase() : 'sin revisar'}.`)
  }

  return (
    <div className="mx-auto max-w-[88rem] px-4 py-6 sm:px-8 sm:py-10">
      <p role="status" aria-live="polite" className="sr-only">
        {state.status === 'loading' ? 'Cargando el ranking de la vacante.' : announcement}
      </p>

      {state.status === 'loading' && <LoadingLayout />}

      {state.status === 'error' && (
        <StateMessage
          tone="error"
          icon={ServerCrash}
          title="No se pudo cargar el ranking"
          description={
            <>
              {state.message} Los CV y las decisiones ya guardadas no se han perdido. Vuelve a intentarlo en unos
              segundos.
            </>
          }
          action={
            <Button onClick={onRetry}>
              <RefreshCw aria-hidden="true" />
              Reintentar
            </Button>
          }
        />
      )}

      {state.status === 'ready' && (
        <Ready vacancy={state.data.vacancy} candidates={state.data.candidates} onDecide={handleDecide} />
      )}
    </div>
  )
}

function VacancyHeader({ vacancy }: { vacancy: Vacancy }) {
  return (
    <header>
      <nav aria-label="Ruta de navegación" className="text-sm text-ink-500">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <AppLink href="/vacantes" className="font-normal text-ink-500 hover:text-navy-900">
              Vacantes
            </AppLink>
          </li>
          <li aria-hidden="true">/</li>
          <li aria-current="page" className="text-ink-700">
            Ranking
          </li>
        </ol>
      </nav>
      <h1 className="mt-3 text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
        {vacancy.title.lead} <span className="font-semibold">{vacancy.title.emphasis}</span>
      </h1>
      <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-700" aria-label="Datos de la vacante">
        <li className="inline-flex items-center gap-1.5">
          <Building2 className="size-4 text-ink-500" aria-hidden="true" />
          {vacancy.company}
        </li>
        <li className="inline-flex items-center gap-1.5">
          <MapPin className="size-4 text-ink-500" aria-hidden="true" />
          {vacancy.location}, {vacancy.modality.toLowerCase()}
        </li>
        <li className="inline-flex items-center gap-1.5">
          <CalendarDays className="size-4 text-ink-500" aria-hidden="true" />
          {vacancy.publishedLabel}
        </li>
      </ul>
    </header>
  )
}

const segments: { key: Decision | 'pendiente'; label: string; bar: string; dot: string }[] = [
  { key: 'preseleccionado', label: 'Preseleccionados', bar: 'bg-white', dot: 'bg-white' },
  { key: 'en_espera', label: 'En espera', bar: 'bg-azure-300', dot: 'bg-azure-300' },
  { key: 'descartado', label: 'Descartados', bar: 'bg-azure-500', dot: 'bg-azure-500' },
  { key: 'pendiente', label: 'Sin revisar', bar: 'bg-white/10', dot: 'bg-white/25 ring-1 ring-white/40' },
]

function ReviewProgress({ candidates }: { candidates: RankedCandidate[] }) {
  const total = candidates.length
  const counts = {
    preseleccionado: candidates.filter((c) => c.decision === 'preseleccionado').length,
    en_espera: candidates.filter((c) => c.decision === 'en_espera').length,
    descartado: candidates.filter((c) => c.decision === 'descartado').length,
    pendiente: candidates.filter((c) => c.decision === null).length,
  }
  const reviewed = total - counts.pendiente

  return (
    <section aria-labelledby="avance" className="relative overflow-hidden rounded-panel bg-navy-900 p-6 text-white shadow-lift sm:p-8">
      <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 id="avance" className="text-sm font-medium text-azure-200">
            Avance de tu revisión
          </h2>
          <p className="mt-2 text-2xl font-light tracking-tight sm:text-3xl">
            {counts.pendiente === 0 ? (
              'Revisaste todos los CV de esta vacante.'
            ) : (
              <>
                Te {counts.pendiente === 1 ? 'falta' : 'faltan'} <span className="font-semibold">{counts.pendiente}</span>{' '}
                de {total} CV por revisar.
              </>
            )}
          </p>
          <p className="mt-2 max-w-lg text-sm leading-relaxed text-azure-200">
            El modelo ordena los CV por compatibilidad. Ninguno avanza ni se descarta sin tu decisión.
          </p>
        </div>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:flex sm:flex-wrap lg:shrink-0 lg:justify-end">
          {segments.map((s) => (
            <div key={s.key} className="flex items-center gap-2 whitespace-nowrap">
              <span aria-hidden="true" className={cn('size-2.5 shrink-0 rounded-full', s.dot)} />
              <dt className="text-azure-200">{s.label}</dt>
              <dd className="font-semibold tabular-nums">{counts[s.key]}</dd>
            </div>
          ))}
        </dl>
      </div>
      <div
        role="img"
        aria-label={`${reviewed} de ${total} CV revisados`}
        className="relative mt-6 flex h-2.5 gap-1 overflow-hidden rounded-full"
      >
        {segments.map((s) =>
          counts[s.key] > 0 ? (
            <div key={s.key} className={cn('h-full rounded-full', s.bar)} style={{ flexGrow: counts[s.key] }} />
          ) : null,
        )}
      </div>
    </section>
  )
}

function ModelAside({ vacancy }: { vacancy: Vacancy }) {
  return (
    <aside className="flex flex-col gap-4 xl:sticky xl:top-8 xl:self-start" aria-label="Cómo se calcula el ranking">
      <section aria-labelledby="criterios" className="rounded-card bg-white p-5 shadow-soft">
        <h2 id="criterios" className="text-base font-semibold text-ink-900">
          Qué evalúa el modelo
        </h2>
        <p className="mt-1 text-sm text-ink-500">Preguntas que se hacen a cada CV para esta vacante.</p>
        <ol className="mt-4 flex flex-col gap-3">
          {vacancy.criteria.map((c) => (
            <li key={c.id} className="text-sm">
              <p className="font-semibold text-ink-900">{c.label}</p>
              <p className="text-ink-700">{c.question}</p>
            </li>
          ))}
        </ol>
        <div className="mt-5 flex items-start gap-2 border-t border-ink-100 pt-4 text-xs text-ink-500">
          <Cpu className="mt-px size-4 shrink-0" aria-hidden="true" />
          <p>
            Modelo <span className="font-semibold text-ink-700">{vacancy.modelVersion}</span>. {vacancy.analyzedLabel}.
          </p>
        </div>
      </section>
      <section aria-labelledby="politica" className="rounded-card bg-azure-100 p-5">
        <h2 id="politica" className="flex items-center gap-2 text-base font-semibold text-navy-900">
          <UserCheck className="size-4" aria-hidden="true" />
          La decisión es tuya
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-700">
          La compatibilidad es una sugerencia para ordenar la revisión. Revisa la evidencia, sobre todo cuando la
          confianza es baja. Las fotos y la edad no se muestran para reducir sesgos.
        </p>
      </section>
    </aside>
  )
}

function Ready({
  vacancy,
  candidates,
  onDecide,
}: {
  vacancy: Vacancy
  candidates: RankedCandidate[]
  onDecide: (c: RankedCandidate, d: Decision | null) => void
}) {
  const [filter, setFilter] = useState<DecisionFilter>('todos')
  const sorted = useMemo(() => [...candidates].sort((a, b) => a.rank - b.rank), [candidates])
  const visible = sorted.filter((c) => matches(c, filter))
  const options = filters.map((f) => ({
    value: f,
    label: filterLabel[f],
    count: candidates.filter((c) => matches(c, f)).length,
  }))

  return (
    <div className="flex flex-col gap-8">
      <VacancyHeader vacancy={vacancy} />

      {candidates.length === 0 ? (
        <StateMessage
          icon={Inbox}
          title="Todavía no hay CV para esta vacante"
          description="Cuando lleguen postulaciones, el modelo las analizará y aparecerán aquí ordenadas por compatibilidad, listas para tu revisión."
        />
      ) : (
        <>
          <ReviewProgress candidates={candidates} />
          <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_20rem]">
            <section aria-labelledby="ranking" className="flex min-w-0 flex-col gap-4">
              <div className="flex flex-col gap-3">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h2 id="ranking" className="text-2xl font-light tracking-tight text-navy-900">
                    Ranking de <span className="font-semibold">candidatos</span>
                  </h2>
                  <p className="text-sm text-ink-500">Ordenado por compatibilidad</p>
                </div>
                <FilterBar label="Filtrar por decisión" options={options} value={filter} onChange={setFilter} />
              </div>

              {visible.length === 0 ? (
                <StateMessage
                  icon={FileSearch}
                  title={`No hay CV en «${filterLabel[filter]}»`}
                  description="Cambia el filtro para ver el resto del ranking."
                  action={
                    <Button variant="secondary" onClick={() => setFilter('todos')}>
                      Ver todos
                    </Button>
                  }
                />
              ) : (
                <ol className="flex flex-col gap-3" aria-label="Candidatos en orden de compatibilidad">
                  {visible.map((c) => (
                    <li key={c.id}>
                      <CandidateRow
                        candidate={c}
                        criteria={vacancy.criteria}
                        onDecide={(_, d) => onDecide(c, d)}
                      />
                    </li>
                  ))}
                </ol>
              )}
            </section>
            <ModelAside vacancy={vacancy} />
          </div>
        </>
      )}
    </div>
  )
}

function LoadingLayout() {
  return (
    <div className="flex flex-col gap-8" aria-busy="true">
      <div className="flex flex-col gap-3">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-11 w-full max-w-lg" />
        <Skeleton className="h-4 w-72 max-w-full" />
      </div>
      <Skeleton className="h-44 rounded-panel" />
      <div className="flex flex-col gap-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex flex-col gap-4 rounded-card bg-white p-5 shadow-soft">
            <div className="flex gap-3">
              <Skeleton className="size-10 rounded-2xl" />
              <div className="flex flex-1 flex-col gap-2">
                <Skeleton className="h-4 w-48 max-w-full" />
                <Skeleton className="h-3 w-64 max-w-full" />
              </div>
            </div>
            <Skeleton className="h-6 w-full max-w-md" />
          </div>
        ))}
      </div>
    </div>
  )
}
