import { BriefcaseBusiness, ChevronRight, MapPin, Plus, Sparkles } from 'lucide-react'
import { AppLink } from '@/components/app-link'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { companyVacancies, type CompanyVacancy } from './api'
import { formatDate, modalityLabel, statusLabel, statusTone } from './labels'
import { LoadError, LoadingRows } from './resource-states'

/** Vacantes de la empresa: borradores, publicadas y cerradas. */
export function CompanyVacanciesPage() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const { state, retry } = useResource(() => companyVacancies.list(token), `vacantes:${token}`, 'No se pudieron cargar las vacantes.')

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
            Tus <span className="font-semibold">vacantes</span>
          </h1>
          <p className="mt-3 text-base text-ink-700">Crea una vacante, define qué se evaluará en cada CV y publícala.</p>
        </div>
        <Button onClick={() => navigate('/vacantes/nueva')}>
          <Plus aria-hidden="true" />
          Nueva vacante
        </Button>
      </header>

      {state.status === 'loading' && <LoadingRows />}
      {state.status === 'error' && <LoadError title="No se pudieron cargar las vacantes" message={state.message} onRetry={retry} />}
      {state.status === 'ready' &&
        (state.data.length === 0 ? (
          <StateMessage
            icon={BriefcaseBusiness}
            title="Todavía no tienes vacantes"
            description="Crea la primera: escribe la descripción del puesto y las preguntas con las que se ordenarán los CV."
            action={<Button onClick={() => navigate('/vacantes/nueva')}>Crear vacante</Button>}
          />
        ) : (
          <ul className="flex flex-col gap-3" aria-label="Vacantes de la empresa">
            {state.data.map((v) => (
              <li key={v.id}>
                <VacancyRow vacancy={v} />
              </li>
            ))}
          </ul>
        ))}

      <p className="flex items-center gap-2 text-sm text-ink-500">
        <Sparkles className="size-4" aria-hidden="true" />
        ¿Quieres ver cómo se ve un ranking con candidatos?{' '}
        <AppLink href="/vacantes/ejemplo">Abrir la vacante de ejemplo</AppLink>
      </p>
    </div>
  )
}

function VacancyRow({ vacancy: v }: { vacancy: CompanyVacancy }) {
  const date =
    v.status === 'DRAFT'
      ? `Editada el ${formatDate(v.updatedAt)}`
      : v.status === 'PUBLISHED'
        ? `Publicada el ${formatDate(v.publishedAt)}`
        : `Cerrada el ${formatDate(v.closedAt)}`
  return (
    <a
      href={`/vacantes/${v.id}`}
      onClick={(e) => {
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
        e.preventDefault()
        navigate(`/vacantes/${v.id}`)
      }}
      className="group flex items-center gap-4 rounded-card bg-white p-5 shadow-soft transition-shadow hover:shadow-lift"
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-base font-semibold text-ink-900 group-hover:text-navy-700">{v.title}</h2>
          <Badge tone={statusTone[v.status]}>{statusLabel[v.status]}</Badge>
        </div>
        <p className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-500">
          <span className="inline-flex items-center gap-1">
            <MapPin className="size-3.5" aria-hidden="true" />
            {v.location}, {modalityLabel[v.modality].toLowerCase()}
          </span>
          <span>
            {v.criteria.length} {v.criteria.length === 1 ? 'criterio' : 'criterios'}
          </span>
          <span>{date}</span>
        </p>
      </div>
      <ChevronRight className="size-5 shrink-0 text-ink-300 group-hover:text-navy-700" aria-hidden="true" />
    </a>
  )
}
