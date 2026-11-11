import { Building2, ChevronRight, FileSearch, ListOrdered, UserCheck } from 'lucide-react'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import { applicationsApi, type MyApplication } from '@/features/application/api'
import { formatDate } from '@/features/vacancy/labels'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'

const steps = [
  {
    icon: ListOrdered,
    title: 'El modelo ordena',
    text: 'Tu CV se compara con los requisitos de la vacante para ordenar la revisión.',
  },
  {
    icon: UserCheck,
    title: 'Una persona decide',
    text: 'Quien recluta revisa tu CV y la evidencia. Ningún CV se descarta de forma automática.',
  },
]

/** Inicio de la persona candidata: sus postulaciones. Nunca muestra rankings ni datos de otras personas. */
export function CandidateHome() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const { state, retry } = useResource(() => applicationsApi.mine(token), `mias:${token}`, 'No se pudieron cargar tus postulaciones.')

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Tus <span className="font-semibold">postulaciones</span>
        </h1>
        <p className="mt-3 max-w-xl text-base text-ink-700">
          Aquí verás cada vacante a la que te postules y en qué etapa está.
        </p>
      </header>

      {state.status === 'loading' && <LoadingRows rows={2} />}
      {state.status === 'error' && (
        <LoadError title="No se pudieron cargar tus postulaciones" message={state.message} onRetry={retry} />
      )}
      {state.status === 'ready' &&
        (state.data.length === 0 ? (
          <StateMessage
            icon={FileSearch}
            title="Todavía no tienes postulaciones"
            description="Sube tu CV y postúlate desde el detalle de cualquier vacante abierta. Aquí seguirás cada proceso."
            action={
              <div className="flex flex-wrap justify-center gap-3">
                <Button onClick={() => navigate('/vacantes')}>Ver vacantes</Button>
                <Button variant="secondary" onClick={() => navigate('/mi-cv')}>
                  Subir mi CV
                </Button>
              </div>
            }
          />
        ) : (
          <ul aria-label="Tus postulaciones" className="divide-y divide-ink-100 rounded-card bg-white shadow-soft">
            {state.data.map((a) => (
              <li key={a.id}>
                <ApplicationRow application={a} />
              </li>
            ))}
          </ul>
        ))}

      <section aria-labelledby="como-se-revisa" className="rounded-card bg-azure-100 p-6">
        <h2 id="como-se-revisa" className="text-base font-semibold text-navy-900">
          Cómo se revisa tu CV
        </h2>
        <ul className="mt-4 grid gap-4 sm:grid-cols-2">
          {steps.map(({ icon: Icon, title, text }) => (
            <li key={title} className="flex gap-3">
              <span className="grid size-9 shrink-0 place-items-center rounded-2xl bg-white text-navy-700">
                <Icon className="size-4" aria-hidden="true" />
              </span>
              <div>
                <p className="text-sm font-semibold text-ink-900">{title}</p>
                <p className="mt-0.5 text-sm leading-relaxed text-ink-700">{text}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

function ApplicationRow({ application: a }: { application: MyApplication }) {
  const open = a.vacancyStatus === 'PUBLISHED'
  const body = (
    <>
      <div className="min-w-0 flex-1">
        <p className="text-base font-semibold text-ink-900">{a.vacancyTitle}</p>
        <p className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-500">
          <span className="inline-flex items-center gap-1">
            <Building2 className="size-3.5" aria-hidden="true" />
            {a.companyName}
          </span>
          <span>Te postulaste el {formatDate(a.appliedAt)}</span>
        </p>
      </div>
      <Badge tone={open ? 'azure' : 'neutral'}>{open ? 'Recibida' : 'Vacante cerrada'}</Badge>
    </>
  )
  // Una vacante cerrada ya no es pública: la fila queda como registro, sin enlace.
  if (!open) return <div className="flex items-center gap-4 px-5 py-4 sm:px-6">{body}</div>
  return (
    <a
      href={`/vacantes/${a.vacancyId}`}
      onClick={(e) => {
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
        e.preventDefault()
        navigate(`/vacantes/${a.vacancyId}`)
      }}
      className="group flex items-center gap-4 px-5 py-4 transition-colors hover:bg-azure-50 sm:px-6"
    >
      {body}
      <ChevronRight className="size-5 shrink-0 text-ink-300 group-hover:text-navy-700" aria-hidden="true" />
    </a>
  )
}
