import { ArrowLeft, Building2, CalendarDays, ChevronRight, Info, MapPin, SearchX } from 'lucide-react'
import type { ReactNode } from 'react'
import { AppLink } from '@/components/app-link'
import { Logo } from '@/components/logo'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { publicVacancies, type PublicVacancySummary } from './api'
import { CriteriaList } from './company-vacancy-pages'
import { formatDate, modalityLabel } from './labels'
import { LoadError, LoadingRows } from './resource-states'

const pageClass = 'mx-auto flex max-w-4xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10'

/** Marco para visitantes sin sesión: cabecera con el logo y el acceso. */
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <header className="bg-navy-900">
        <div className="mx-auto flex h-16 max-w-4xl items-center justify-between px-4 sm:px-8">
          <a
            href="/vacantes"
            onClick={(e) => {
              e.preventDefault()
              navigate('/vacantes')
            }}
            className="rounded-full focus-visible:outline-azure-300"
          >
            <Logo />
          </a>
          <Button variant="inverse" size="sm" onClick={() => navigate('/')}>
            Iniciar sesión
          </Button>
        </div>
      </header>
      <main id="contenido">{children}</main>
    </div>
  )
}

/** Lista de vacantes publicadas (candidatos y visitantes). */
export function PublicVacanciesPage() {
  const { state, retry } = useResource(() => publicVacancies.list(), 'publicas', 'No se pudieron cargar las vacantes.')
  return (
    <div className={pageClass}>
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Vacantes <span className="font-semibold">abiertas</span>
        </h1>
        <p className="mt-3 text-base text-ink-700">
          En cada una puedes ver qué se evaluará en tu CV. Una persona revisa siempre la decisión.
        </p>
      </header>
      {state.status === 'loading' && <LoadingRows />}
      {state.status === 'error' && <LoadError title="No se pudieron cargar las vacantes" message={state.message} onRetry={retry} />}
      {state.status === 'ready' &&
        (state.data.length === 0 ? (
          <StateMessage
            icon={SearchX}
            title="No hay vacantes abiertas ahora"
            description="Las empresas publican vacantes con frecuencia. Vuelve a revisar en unos días."
          />
        ) : (
          <ul className="flex flex-col gap-3" aria-label="Vacantes abiertas">
            {state.data.map((v) => (
              <li key={v.id}>
                <SummaryRow vacancy={v} />
              </li>
            ))}
          </ul>
        ))}
    </div>
  )
}

function SummaryRow({ vacancy: v }: { vacancy: PublicVacancySummary }) {
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
        <h2 className="text-base font-semibold text-ink-900 group-hover:text-navy-700">{v.title}</h2>
        <p className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-500">
          <span className="inline-flex items-center gap-1">
            <Building2 className="size-3.5" aria-hidden="true" />
            {v.companyName}
          </span>
          <span className="inline-flex items-center gap-1">
            <MapPin className="size-3.5" aria-hidden="true" />
            {v.location}, {modalityLabel[v.modality].toLowerCase()}
          </span>
          <span>Publicada el {formatDate(v.publishedAt)}</span>
        </p>
      </div>
      <ChevronRight className="size-5 shrink-0 text-ink-300 group-hover:text-navy-700" aria-hidden="true" />
    </a>
  )
}

/** Detalle público: descripción y preguntas. `signedIn` cambia la invitación final. */
export function PublicVacancyPage({ id, signedIn }: { id: string; signedIn: boolean }) {
  const { state, retry } = useResource(() => publicVacancies.get(id), `publica:${id}`, 'No se pudo cargar la vacante.')
  const back = (
    <AppLink href="/vacantes" className="inline-flex items-center gap-1.5 text-sm">
      <ArrowLeft className="size-4" aria-hidden="true" />
      Vacantes abiertas
    </AppLink>
  )
  if (state.status !== 'ready') {
    return (
      <div className={pageClass}>
        {back}
        {state.status === 'loading' ? (
          <LoadingRows />
        ) : (
          <LoadError title="No se pudo cargar la vacante" message={state.message} onRetry={retry} />
        )}
      </div>
    )
  }
  const v = state.data
  return (
    <div className={pageClass}>
      <header>
        {back}
        <h1 className="mt-4 text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">{v.title}</h1>
        <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-700" aria-label="Datos de la vacante">
          <li className="inline-flex items-center gap-1.5">
            <Building2 className="size-4 text-ink-500" aria-hidden="true" />
            {v.companyName}
          </li>
          <li className="inline-flex items-center gap-1.5">
            <MapPin className="size-4 text-ink-500" aria-hidden="true" />
            {v.location}, {modalityLabel[v.modality].toLowerCase()}
          </li>
          <li className="inline-flex items-center gap-1.5">
            <CalendarDays className="size-4 text-ink-500" aria-hidden="true" />
            Publicada el {formatDate(v.publishedAt)}
          </li>
        </ul>
      </header>

      <section aria-labelledby="sobre-puesto" className="rounded-card bg-white p-5 shadow-soft sm:p-6">
        <h2 id="sobre-puesto" className="text-lg font-semibold text-ink-900">
          Sobre el puesto
        </h2>
        <p className="mt-2 text-base leading-relaxed whitespace-pre-line text-ink-700">{v.description}</p>
      </section>

      <CriteriaList criteria={v.criteria} title="Qué se evaluará en tu CV" />

      <p className="flex gap-3 rounded-card bg-azure-100 p-5 text-sm leading-relaxed text-ink-700">
        <Info className="mt-0.5 size-4 shrink-0 text-navy-700" aria-hidden="true" />
        {signedIn ? (
          <span>Pronto podrás postularte desde aquí. Un modelo ordenará los CV y una persona revisará cada uno.</span>
        ) : (
          <span>
            Pronto podrás postularte con tu cuenta de Worksy. <AppLink href="/">Inicia sesión</AppLink> para estar listo.
          </span>
        )}
      </p>
    </div>
  )
}
