import { ArrowLeft, Building2, CalendarDays, ArrowUpRight, Info, MapPin, RefreshCw, Search, SearchX } from 'lucide-react'
import { useRef, type ReactNode } from 'react'
import { AppLink } from '@/components/app-link'
import { PageHeader } from '@/components/page-header'
import { Logo } from '@/components/logo'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApplyPanel } from '@/features/application/apply-panel'
import { navigate, useLocation } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { publicVacancies, type PublicVacancySummary } from './api'
import { formatDate, modalityLabel } from './labels'
import { LoadError, LoadingRows } from './resource-states'
import { OfferContent } from './offer-details'

const pageClass = 'app-page'
const fold = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es').trim()
const PAGE_SIZE = 12

/** Marco para visitantes sin sesión: cabecera con el logo y el acceso. */
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <header className="bg-navy-900">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-8">
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
      <main id="contenido" className="mx-auto max-w-6xl">{children}</main>
    </div>
  )
}

/** Lista de vacantes publicadas (candidatos y visitantes). */
export function PublicVacanciesPage() {
  const { state, retry } = useResource(() => publicVacancies.list(), 'publicas', 'No se pudieron cargar las vacantes.')
  const { params } = useLocation()
  const resultsRef = useRef<HTMLDivElement>(null)
  const query = (params.get('q') ?? '').slice(0, 120)
  const location = (params.get('ubicacion') ?? '').slice(0, 100)
  const modality = ['PRESENCIAL', 'HIBRIDO', 'REMOTO'].includes(params.get('modalidad') ?? '') ? params.get('modalidad')! : ''
  const order = ['antiguas', 'titulo'].includes(params.get('orden') ?? '') ? params.get('orden')! : 'recientes'
  const all = state.status === 'ready' ? state.data : []
  const words = fold(query).split(/\s+/).filter(Boolean)
  const filtered = all.filter((v) => words.every((word) => fold(`${v.title} ${v.companyName}`).includes(word))
    && fold(v.location).includes(fold(location)) && (!modality || modality === v.modality)).sort((a, b) => {
      const primary = order === 'titulo' ? a.title.localeCompare(b.title, 'es')
        : (Date.parse(a.publishedAt) - Date.parse(b.publishedAt)) * (order === 'antiguas' ? 1 : -1)
      return primary || a.id.localeCompare(b.id)
    })
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const rawPage = Number(params.get('pagina') || '1')
  const page = Number.isSafeInteger(rawPage) ? Math.min(pages, Math.max(1, rawPage)) : 1
  const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
  const hasFilters = !!query.trim() || !!location.trim() || !!modality
  function change(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'pagina') next.delete('pagina')
    navigate(`/vacantes${next.size ? `?${next}` : ''}`, { replace: key !== 'pagina' })
    if (key === 'pagina') requestAnimationFrame(() => {
      resultsRef.current?.focus({ preventScroll: true })
      resultsRef.current?.scrollIntoView?.({ block: 'start', behavior: 'auto' })
    })
  }
  return (
    <div className={pageClass}>
      <PageHeader title="Vacantes abiertas" description="Encuentra una oportunidad y consulta sus requisitos antes de postular."
        actions={<Button variant="secondary" size="sm" disabled={state.status === 'loading'} onClick={retry}><RefreshCw aria-hidden="true" />Actualizar vacantes</Button>} />
      <section aria-label="Buscar y filtrar vacantes" className="grid gap-4 rounded-card border border-ink-100 bg-white p-4 sm:grid-cols-2 xl:grid-cols-[2fr_1.4fr_1fr]">
        <Field id="buscar-vacantes" label="Puesto o empresa"><div className="relative"><Search className="pointer-events-none absolute top-3 left-3 size-4 text-ink-500" aria-hidden="true" />
          <Input id="buscar-vacantes" type="search" maxLength={120} className="pl-10" placeholder="Por ejemplo: backend o nombre de empresa" value={query} onChange={(e) => change('q', e.target.value)} /></div></Field>
        <Field id="ubicacion-vacantes" label="Ubicación"><Input id="ubicacion-vacantes" type="search" maxLength={100} placeholder="Ciudad o país" value={location} onChange={(e) => change('ubicacion', e.target.value)} /></Field>
        <Field id="modalidad-vacantes" label="Modalidad"><select id="modalidad-vacantes" value={modality} onChange={(e) => change('modalidad', e.target.value)} className="h-10 min-w-0 rounded-md border border-ink-100 bg-white px-3 text-sm">
          <option value="">Todas las modalidades</option><option value="PRESENCIAL">Presencial</option><option value="HIBRIDO">Híbrido</option><option value="REMOTO">Remoto</option>
        </select></Field>
      </section>
      {state.status === 'loading' && <LoadingRows />}
      {state.status === 'error' && <LoadError title="No se pudieron cargar las vacantes" message={state.message} onRetry={retry} />}
      {state.status === 'ready' &&
        (state.data.length === 0 ? (
          <StateMessage
            icon={SearchX}
            title="No hay vacantes abiertas ahora"
            description="No hay ofertas publicadas por el momento. Puedes volver a consultar más tarde."
            className="rounded-card py-8"
          />
        ) : (
          <>
          {all.length >= 100 && <p role="note" className="rounded-md bg-mid-100 px-4 py-3 text-xs leading-5 text-ink-700">Esta vista carga hasta 100 vacantes recientes. La búsqueda, los filtros y el orden se aplican a ese conjunto.</p>}
          <div ref={resultsRef} tabIndex={-1} className="flex scroll-mt-4 flex-wrap items-center justify-between gap-3">
            <p role="status" className="text-sm text-ink-700">{filtered.length} {filtered.length === 1 ? 'resultado' : 'resultados'} de {all.length} vacantes cargadas</p>
            <div className="flex flex-wrap items-center gap-3">
              {hasFilters && <Button variant="ghost" size="sm" onClick={() => navigate('/vacantes', { replace: true })}>Limpiar filtros</Button>}
              <label htmlFor="orden-vacantes" className="text-sm text-ink-700">Ordenar por</label>
              <select id="orden-vacantes" value={order} onChange={(e) => change('orden', e.target.value)} className="h-10 min-w-0 rounded-md border border-ink-100 bg-white px-3 text-sm">
                <option value="recientes">Más recientes</option><option value="antiguas">Más antiguas</option><option value="titulo">Título: A–Z</option>
              </select>
            </div>
          </div>
          {filtered.length === 0 ? <StateMessage icon={SearchX} title="No encontramos vacantes con esos filtros" description="Prueba otro puesto, empresa o ubicación, o elimina los filtros para ver las vacantes disponibles." className="rounded-card py-8"
            action={<Button variant="secondary" onClick={() => navigate('/vacantes', { replace: true })}>Ver todas las vacantes</Button>} /> : <>
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Vacantes abiertas">
            {visible.map((v) => (
              <li key={v.id}>
                <SummaryRow vacancy={v} returnTo={`/vacantes${params.size ? `?${params}` : ''}`} />
              </li>
            ))}
          </ul>
          <nav aria-label="Páginas de vacantes" className="flex flex-wrap items-center justify-between gap-3 border-t border-ink-100 pt-4">
            <p className="text-xs text-ink-500">Mostrando {(page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, filtered.length)} de {filtered.length}</p>
            <div className="flex items-center gap-3"><Button variant="secondary" size="sm" disabled={page === 1} onClick={() => change('pagina', String(page - 1))}>Anterior</Button>
              <span className="text-sm text-ink-700">Página {page} de {pages}</span><Button variant="secondary" size="sm" disabled={page === pages} onClick={() => change('pagina', String(page + 1))}>Siguiente</Button></div>
          </nav>
          </>}
          </>
        ))}
    </div>
  )
}

function SummaryRow({ vacancy: v, returnTo }: { vacancy: PublicVacancySummary; returnTo: string }) {
  const href = `/vacantes/${v.id}${returnTo === '/vacantes' ? '' : `?volver=${encodeURIComponent(returnTo)}`}`
  return (
    <article className="flex h-full min-w-0 flex-col rounded-card border border-ink-100 bg-white p-4 shadow-soft">
      <div className="mb-4"><Badge tone="azure">{modalityLabel[v.modality]}</Badge></div>
      <h2 className="break-words text-base font-semibold leading-6 text-ink-900">{v.title}</h2>
      <p className="mt-2 flex min-w-0 items-start gap-2 break-words text-sm text-ink-700"><Building2 className="mt-0.5 size-4 shrink-0 text-ink-500" aria-hidden="true" />{v.companyName}</p>
      <p className="mt-3 flex min-w-0 items-start gap-2 break-words text-sm text-ink-500"><MapPin className="mt-0.5 size-4 shrink-0" aria-hidden="true" />{v.location || 'Ubicación no indicada'}</p>
      <p className="mt-3 mb-4 text-xs leading-5 text-ink-500">Publicada el <time dateTime={v.publishedAt}>{formatDate(v.publishedAt)}</time></p>
      <a
      href={href}
      onClick={(e) => {
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
        e.preventDefault()
        navigate(href)
      }}
      aria-label={`Ver vacante: ${v.title} en ${v.companyName}`}
      className="mt-auto flex min-h-10 items-center justify-between gap-2 rounded-lg border-t border-ink-100 pt-4 text-sm font-semibold text-azure-600 hover:text-navy-900 hover:underline"
    >
      Ver vacante<ArrowUpRight className="size-4 shrink-0" aria-hidden="true" />
    </a>
    </article>
  )
}

/** Detalle público: descripción y preguntas. `signedIn` cambia la invitación final. */
export function PublicVacancyPage({ id, signedIn }: { id: string; signedIn: boolean }) {
  const { params } = useLocation()
  const requestedBack = params.get('volver') ?? ''
  const returnTo = requestedBack.length <= 1000 && (requestedBack === '/vacantes' || requestedBack.startsWith('/vacantes?')) ? requestedBack : '/vacantes'
  const { state, retry } = useResource(() => publicVacancies.get(id), `publica:${id}`, 'No se pudo cargar la vacante.')
  const back = (
    <AppLink href={returnTo} className="inline-flex items-center gap-1.5 text-sm">
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
        <h1 className="mt-4 break-words text-2xl font-semibold leading-tight tracking-tight text-navy-900">{v.title}</h1>
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

      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(280px,1fr)]">
      <div className="flex min-w-0 flex-col gap-5">
      <section aria-labelledby="sobre-puesto" className="rounded-card border border-ink-100 bg-white p-4 shadow-soft">
        <h2 id="sobre-puesto" className="text-lg font-semibold text-ink-900">
          Sobre el puesto
        </h2>
        <p className="mt-2 break-words text-sm leading-relaxed whitespace-pre-line text-ink-700">{v.description}</p>
      </section>
      {v.offer && <OfferContent offer={v.offer} />}

      </div>
      <aside aria-label="Postulación a la vacante" className="min-w-0 xl:sticky xl:top-6">
      {signedIn ? (
        <ApplyPanel vacancyId={v.id} companyName={v.companyName} />
      ) : (
        <p className="flex gap-3 rounded-card bg-azure-100 p-5 text-sm leading-relaxed text-ink-700">
          <Info className="mt-0.5 size-4 shrink-0 text-navy-700" aria-hidden="true" />
          <span>
            <AppLink href="/">Inicia sesión</AppLink> con tu cuenta de Worksy para postularte. Un modelo ordenará los CV
            y una persona revisará cada uno.
          </span>
        </p>
      )}
      </aside>
      </div>
    </div>
  )
}
