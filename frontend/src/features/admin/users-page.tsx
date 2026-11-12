import { RefreshCw, Search, Users } from 'lucide-react'
import { useState } from 'react'
import { StateMessage } from '@/components/state-message'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { Role } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { roleLabel } from '@/features/home/navigation'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { useResource } from '@/lib/use-resource'
import { listPlatformUsers } from './api'

const roles = Object.keys(roleLabel) as Role[]
const date = new Intl.DateTimeFormat('es', { dateStyle: 'medium' })
const number = new Intl.NumberFormat('es')

export function PlatformUsersPage() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const currentUserId = auth.status === 'authenticated' ? auth.user.id : null
  const [draft, setDraft] = useState('')
  const [draftRole, setDraftRole] = useState<Role | ''>('')
  const [query, setQuery] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [page, setPage] = useState(0)
  const { state, retry } = useResource(
    () => listPlatformUsers(token, query, role, page),
    `admin-usuarios:${token}:${JSON.stringify([query, role, page])}`,
    'No se pudieron cargar los usuarios.',
  )
  const filtered = query !== '' || role !== ''

  function clearFilters() {
    setDraft('')
    setDraftRole('')
    setQuery('')
    setRole('')
    setPage(0)
  }

  function applyFilters() {
    const nextQuery = draft.trim()
    if (nextQuery === query && draftRole === role && page === 0) retry()
    setQuery(nextQuery)
    setRole(draftRole)
    setPage(0)
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Usuarios de <span className="font-semibold">Worksy</span>
        </h1>
        <p className="mt-3 max-w-xl text-base text-ink-700">Consulta las cuentas activas, sus roles y las empresas a las que pertenecen.</p>
      </header>

      <section aria-label="Usuarios de la plataforma" className="flex flex-col gap-4">
        <form className="flex flex-col gap-3" onSubmit={(event) => { event.preventDefault(); applyFilters() }}>
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_16rem]">
            <div className="flex flex-col gap-2">
              <label htmlFor="buscar-usuario" className="text-sm font-semibold text-ink-900">Buscar por correo o empresa</label>
              <Input id="buscar-usuario" type="search" maxLength={254} placeholder="Correo o nombre de empresa"
                value={draft} onChange={(event) => setDraft(event.target.value)} />
            </div>
            <div className="flex flex-col gap-2">
              <label htmlFor="rol-usuario" className="text-sm font-semibold text-ink-900">Rol</label>
              <select id="rol-usuario" value={draftRole} onChange={(event) => setDraftRole(event.target.value as Role | '')}
                className="h-12 w-full rounded-2xl bg-white px-4 text-base text-ink-900 ring-1 ring-ink-300 ring-inset focus-visible:ring-2 focus-visible:ring-azure-500 focus-visible:outline-none">
                <option value="">Todos los roles</option>
                {roles.map((option) => <option key={option} value={option}>{roleLabel[option]}</option>)}
              </select>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit"><Search aria-hidden="true" /> Filtrar usuarios</Button>
            {filtered && <Button type="button" variant="secondary" onClick={clearFilters}>Limpiar filtros</Button>}
            <Button type="button" variant="secondary" onClick={retry} disabled={state.status === 'loading'}>
              <RefreshCw aria-hidden="true" /> Actualizar usuarios
            </Button>
          </div>
        </form>

        {state.status === 'loading' && <LoadingRows />}
        {state.status === 'error' && <LoadError title="No se pudieron cargar los usuarios" message={state.message} onRetry={retry} />}
        {state.status === 'ready' && (
          <>
            <p role="status" className="text-sm text-ink-700">
              {number.format(state.data.total)} {state.data.total === 1
                ? `cuenta ${filtered ? 'encontrada' : 'activa'}`
                : `cuentas ${filtered ? 'encontradas' : 'activas'}`}
            </p>
            {state.data.items.length === 0 ? (
              <StateMessage icon={filtered ? Search : Users}
                title={filtered ? 'No hay usuarios con estos filtros' : 'Todavía no hay usuarios para mostrar'}
                description={filtered ? 'Prueba con otro correo, empresa o rol.' : 'Las cuentas activas aparecerán aquí cuando se registren.'}
                action={filtered ? <Button variant="secondary" onClick={clearFilters}>Ver todos los usuarios</Button> : undefined} />
            ) : (
              <div role="region" aria-label="Listado de usuarios" tabIndex={0} className="overflow-x-auto rounded-card bg-white shadow-soft">
                <table className="w-full text-left text-sm">
                  <caption className="sr-only">Cuentas activas, de la más reciente a la más antigua, con rol, empresa y fecha de registro.</caption>
                  <thead className="border-b border-ink-100 bg-azure-50 text-ink-700">
                    <tr>
                      <th scope="col" className="px-4 py-3">Correo</th>
                      <th scope="col" className="px-4 py-3">Rol</th>
                      <th scope="col" className="px-4 py-3">Empresa</th>
                      <th scope="col" className="px-4 py-3">Registro</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-100">
                    {state.data.items.map((user) => (
                      <tr key={user.id}>
                        <th scope="row" className="min-w-48 px-4 py-4 font-semibold text-ink-900">
                          <span className="break-all">{user.email}</span>
                          {user.id === currentUserId && <Badge tone="azure" className="ml-2">Tu cuenta</Badge>}
                        </th>
                        <td className="px-4 py-4"><Badge>{roleLabel[user.role]}</Badge></td>
                        <td className="px-4 py-4 text-ink-700">{user.companyName ?? 'Sin empresa'}</td>
                        <td className="px-4 py-4 whitespace-nowrap text-ink-700">{date.format(new Date(user.createdAt))}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {state.data.total > 0 && (
              <nav aria-label="Páginas de usuarios" className="flex flex-wrap items-center justify-between gap-3">
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
