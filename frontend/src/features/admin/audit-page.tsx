import { RefreshCw, Search, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/auth-context'
import { roleLabel } from '@/features/home/navigation'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { useResource } from '@/lib/use-resource'
import { auditActionLabel, listAuditEvents, type AuditAction } from './audit-api'

const actions = Object.keys(auditActionLabel) as AuditAction[]
const date = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })
const number = new Intl.NumberFormat('es')

export function PlatformAuditPage() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const [draft, setDraft] = useState('')
  const [draftAction, setDraftAction] = useState<AuditAction | ''>('')
  const [query, setQuery] = useState('')
  const [action, setAction] = useState<AuditAction | ''>('')
  const [page, setPage] = useState(0)
  const { state, retry } = useResource(
    () => listAuditEvents(token, query, action, page),
    `auditoria:${token}:${JSON.stringify([query, action, page])}`,
    'No se pudo cargar la auditoría.',
  )
  const filtered = query !== '' || action !== ''

  function clearFilters() {
    setDraft('')
    setDraftAction('')
    setQuery('')
    setAction('')
    setPage(0)
  }

  function applyFilters() {
    const nextQuery = draft.trim()
    if (nextQuery === query && draftAction === action && page === 0) retry()
    setQuery(nextQuery)
    setAction(draftAction)
    setPage(0)
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Auditoría de <span className="font-semibold">Worksy</span>
        </h1>
        <p className="mt-3 max-w-2xl text-base text-ink-700">Registro de empresas, cambios de vacantes, solicitudes de análisis y decisiones humanas.</p>
        <p className="mt-2 text-sm text-ink-500">El registro comienza al instalar esta versión. No incluye acciones anteriores.</p>
      </header>

      <section aria-label="Acciones registradas" className="flex flex-col gap-4">
        <form className="flex flex-col gap-3" onSubmit={(event) => { event.preventDefault(); applyFilters() }}>
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_18rem]">
            <div className="flex flex-col gap-2">
              <label htmlFor="buscar-auditoria" className="text-sm font-semibold text-ink-900">Buscar por autor, empresa o recurso</label>
              <Input id="buscar-auditoria" type="search" maxLength={254} placeholder="Correo, empresa o título"
                value={draft} onChange={(event) => setDraft(event.target.value)} />
            </div>
            <div className="flex flex-col gap-2">
              <label htmlFor="accion-auditoria" className="text-sm font-semibold text-ink-900">Acción</label>
              <select id="accion-auditoria" value={draftAction} onChange={(event) => setDraftAction(event.target.value as AuditAction | '')}
                className="h-12 w-full rounded-2xl bg-white px-4 text-base text-ink-900 ring-1 ring-ink-300 ring-inset focus-visible:ring-2 focus-visible:ring-azure-500 focus-visible:outline-none">
                <option value="">Todas las acciones</option>
                {actions.map((option) => <option key={option} value={option}>{auditActionLabel[option]}</option>)}
              </select>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit"><Search aria-hidden="true" /> Filtrar acciones</Button>
            {filtered && <Button type="button" variant="secondary" onClick={clearFilters}>Limpiar filtros</Button>}
            <Button type="button" variant="secondary" onClick={retry} disabled={state.status === 'loading'}>
              <RefreshCw aria-hidden="true" /> Actualizar registro
            </Button>
          </div>
        </form>

        {state.status === 'loading' && <LoadingRows />}
        {state.status === 'error' && <LoadError title="No se pudo cargar la auditoría" message={state.message} onRetry={retry} />}
        {state.status === 'ready' && (
          <>
            <p role="status" className="text-sm text-ink-700">
              {number.format(state.data.total)} {state.data.total === 1
                ? `acción ${filtered ? 'encontrada' : 'registrada'}`
                : `acciones ${filtered ? 'encontradas' : 'registradas'}`}
            </p>
            {state.data.items.length === 0 ? (
              <StateMessage icon={filtered ? Search : ShieldCheck}
                title={filtered ? 'No hay acciones con estos filtros' : 'Todavía no hay acciones registradas'}
                description={filtered ? 'Prueba con otro autor, empresa, recurso o acción.' : 'Las nuevas acciones cubiertas por la auditoría aparecerán aquí.'}
                action={filtered ? <Button variant="secondary" onClick={clearFilters}>Ver todas las acciones</Button> : undefined} />
            ) : (
              <div role="region" aria-label="Registro de auditoría" tabIndex={0} className="overflow-x-auto rounded-card bg-white shadow-soft">
                <table className="w-full text-left text-sm">
                  <caption className="sr-only">Acciones registradas, de la más reciente a la más antigua.</caption>
                  <thead className="border-b border-ink-100 bg-azure-50 text-ink-700">
                    <tr>
                      <th scope="col" className="px-4 py-3">Fecha y hora</th>
                      <th scope="col" className="px-4 py-3">Autor</th>
                      <th scope="col" className="px-4 py-3">Empresa</th>
                      <th scope="col" className="px-4 py-3">Acción</th>
                      <th scope="col" className="px-4 py-3">Recurso</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-100">
                    {state.data.items.map((entry) => (
                      <tr key={entry.id}>
                        <td className="px-4 py-4 whitespace-nowrap text-ink-700">{date.format(new Date(entry.occurredAt))}</td>
                        <td className="px-4 py-4">
                          <p className="break-all font-semibold text-ink-900">{entry.actorEmail}</p>
                          <p className="mt-1 text-xs text-ink-500">{roleLabel[entry.actorRole]}</p>
                        </td>
                        <td className="px-4 py-4 text-ink-700">{entry.companyName ?? 'Sin empresa'}</td>
                        <td className="px-4 py-4"><Badge>{auditActionLabel[entry.action]}</Badge></td>
                        <td className="min-w-48 px-4 py-4 text-ink-900">
                          <p>{entry.targetLabel}</p>
                          <details className="mt-1 text-xs text-ink-500">
                            <summary className="cursor-pointer">Identificar recurso</summary>
                            <p className="mt-1 break-all font-mono">{entry.targetId}</p>
                          </details>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {state.data.total > 0 && (
              <nav aria-label="Páginas de auditoría" className="flex flex-wrap items-center justify-between gap-3">
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
