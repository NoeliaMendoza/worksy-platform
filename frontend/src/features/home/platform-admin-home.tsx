import { Building2, RefreshCw, Search } from 'lucide-react'
import { useState } from 'react'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/auth-context'
import { listPlatformCompanies } from '@/features/company/admin-api'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { useResource } from '@/lib/use-resource'

const date = new Intl.DateTimeFormat('es', { dateStyle: 'medium' })
const number = new Intl.NumberFormat('es')

/** Inicio de la administración de Worksy: empresas de la plataforma. */
export function PlatformAdminHome() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const [draft, setDraft] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const { state, retry } = useResource(
    () => listPlatformCompanies(token, query, page),
    `admin-empresas:${token}:${JSON.stringify([query, page])}`,
    'No se pudieron cargar las empresas.',
  )

  function clearSearch() {
    setDraft('')
    setQuery('')
    setPage(0)
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Administración de <span className="font-semibold">Worksy</span>
        </h1>
        <p className="mt-3 max-w-xl text-base text-ink-700">Empresas registradas, sus equipos y la actividad de sus vacantes.</p>
      </header>

      <section aria-labelledby="empresas-plataforma" className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="empresas-plataforma" className="text-2xl font-semibold text-navy-900">Empresas</h2>
          <Button variant="secondary" onClick={retry} disabled={state.status === 'loading'}>
            <RefreshCw aria-hidden="true" /> Actualizar empresas
          </Button>
        </div>
        <form className="flex flex-col gap-2" onSubmit={(event) => {
          event.preventDefault()
          setPage(0)
          setQuery(draft.trim())
        }}>
          <label htmlFor="buscar-empresa" className="text-sm font-semibold text-ink-900">Buscar por nombre de empresa</label>
          <div className="flex flex-wrap gap-2">
            <Input id="buscar-empresa" type="search" maxLength={160} value={draft}
              onChange={(event) => setDraft(event.target.value)} placeholder="Nombre de la empresa" className="min-w-48 flex-1" />
            <Button type="submit"><Search aria-hidden="true" /> Buscar</Button>
            {query && <Button type="button" variant="secondary" onClick={clearSearch}>Limpiar búsqueda</Button>}
          </div>
        </form>

        {state.status === 'loading' && <LoadingRows />}
        {state.status === 'error' && <LoadError title="No se pudieron cargar las empresas" message={state.message} onRetry={retry} />}
        {state.status === 'ready' && (
          <>
            <p role="status" className="text-sm text-ink-700">
              {number.format(state.data.total)} {state.data.total === 1
                ? `empresa ${query ? 'encontrada' : 'registrada'}`
                : `empresas ${query ? 'encontradas' : 'registradas'}`}
            </p>
            {state.data.items.length === 0 ? (
              <StateMessage icon={query ? Search : Building2}
                title={query ? 'No se encontraron empresas' : 'Todavía no hay empresas registradas'}
                description={query ? 'Prueba con otra parte del nombre o limpia la búsqueda.' : 'Las empresas aparecerán aquí cuando se registren en Worksy.'}
                action={query ? <Button variant="secondary" onClick={clearSearch}>Ver todas las empresas</Button> : undefined} />
            ) : (
              <>
                <div role="region" aria-label="Listado de empresas" tabIndex={0} className="overflow-x-auto rounded-card bg-white shadow-soft">
                  <table className="w-full text-left text-sm">
                    <caption className="sr-only">Empresas registradas, de la más reciente a la más antigua, con sus equipos y vacantes.</caption>
                    <thead className="border-b border-ink-100 bg-azure-50 text-ink-700">
                      <tr>
                        <th scope="col" className="px-4 py-3">Empresa</th>
                        <th scope="col" className="px-4 py-3">Registro</th>
                        <th scope="col" className="px-4 py-3 text-right">Administradores</th>
                        <th scope="col" className="px-4 py-3 text-right">Reclutadores</th>
                        <th scope="col" className="px-4 py-3 text-right">Vacantes</th>
                        <th scope="col" className="px-4 py-3 text-right">Publicadas</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100">
                      {state.data.items.map((company) => (
                        <tr key={company.id}>
                          <th scope="row" className="min-w-48 px-4 py-4 font-semibold text-ink-900">{company.name}</th>
                          <td className="px-4 py-4 whitespace-nowrap text-ink-700">{date.format(new Date(company.createdAt))}</td>
                          <td className="px-4 py-4 text-right tabular-nums">{number.format(company.administrators)}</td>
                          <td className="px-4 py-4 text-right tabular-nums">{number.format(company.recruiters)}</td>
                          <td className="px-4 py-4 text-right tabular-nums">{number.format(company.vacancies)}</td>
                          <td className="px-4 py-4 text-right tabular-nums">{number.format(company.publishedVacancies)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="text-xs text-ink-500">El equipo incluye cuentas activas. El total de vacantes incluye borradores, publicadas y cerradas.</p>
              </>
            )}
            {state.data.total > 0 && (
              <nav aria-label="Páginas de empresas" className="flex flex-wrap items-center justify-between gap-3">
                <Button variant="secondary" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</Button>
                <p className="text-sm text-ink-700">Página {page + 1} de {Math.max(1, Math.ceil(state.data.total / state.data.size))}</p>
                <Button variant="secondary" disabled={(page + 1) * state.data.size >= state.data.total}
                  onClick={() => setPage((value) => value + 1)}>Siguiente</Button>
              </nav>
            )}
          </>
        )}
      </section>
    </div>
  )
}
