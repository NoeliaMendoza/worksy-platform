import { BriefcaseBusiness, ChevronRight, MapPin, Plus, SearchX } from 'lucide-react'
import { useState } from 'react'
import { PageHeader } from '@/components/page-header'
import { Input } from '@/components/ui/input'
import { Field } from '@/components/ui/field'
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
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const fold = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es').trim()
  const vacancies = state.status === 'ready' ? state.data : []
  const filtered = vacancies.filter((v) => (!status || v.status === status) && fold(`${v.title} ${v.location}`).includes(fold(query)))

  return (
    <div className="app-page">
      <PageHeader title="Tus vacantes" description="Gestiona borradores, revisa postulaciones y administra las vacantes publicadas."
        actions={<Button onClick={() => navigate('/vacantes/nueva')}>
          <Plus aria-hidden="true" />
          Nueva vacante
        </Button>} />

      {state.status === 'ready' && vacancies.length > 0 && <>
        <section aria-label="Buscar y filtrar vacantes de la empresa" className="grid gap-4 rounded-card border border-ink-100 bg-white p-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <Field id="buscar-vacantes-empresa" label="Puesto o ubicación"><Input id="buscar-vacantes-empresa" type="search" value={query} maxLength={160} placeholder="Buscar entre tus vacantes" onChange={(e) => setQuery(e.target.value)} /></Field>
          <Field id="estado-vacantes-empresa" label="Estado"><select id="estado-vacantes-empresa" value={status} onChange={(e) => setStatus(e.target.value)} className="h-10 min-w-0 rounded-md border border-ink-100 bg-white px-3 text-base sm:text-sm">
            <option value="">Todos los estados</option><option value="DRAFT">Borrador</option><option value="PUBLISHED">Publicada</option><option value="CLOSED">Cerrada</option>
          </select></Field>
        </section>
        <p role="status" className="text-sm text-ink-700">{filtered.length} de {vacancies.length} vacantes</p>
      </>}

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
          filtered.length === 0 ? <StateMessage icon={SearchX} title="No hay vacantes con esos filtros" description="Prueba otro puesto o ubicación, o consulta todos los estados."
            action={<Button variant="secondary" onClick={() => { setQuery(''); setStatus('') }}>Limpiar filtros</Button>} /> : <ul className="flex flex-col gap-3" aria-label="Vacantes de la empresa">
            {filtered.map((v) => (
              <li key={v.id}>
                <VacancyRow vacancy={v} />
              </li>
            ))}
          </ul>
        ))}

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
      className="group flex min-w-0 items-center gap-4 rounded-card border border-ink-100 bg-white p-4 shadow-soft transition-shadow hover:shadow-lift"
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="break-words text-base font-semibold text-ink-900 group-hover:text-navy-700">{v.title}</h2>
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
