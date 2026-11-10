import {
  BriefcaseBusiness,
  LayoutGrid,
  LogOut,
  Menu,
  MessageSquare,
  Users,
  Video,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useRef, useState, type ReactNode } from 'react'
import { Logo } from '@/components/logo'
import { cn } from '@/lib/cn'

type NavItem = { id: string; label: string; icon: LucideIcon }

const nav: NavItem[] = [
  { id: 'inicio', label: 'Inicio', icon: LayoutGrid },
  { id: 'vacantes', label: 'Vacantes', icon: BriefcaseBusiness },
  { id: 'candidatos', label: 'Candidatos', icon: Users },
  { id: 'entrevistas', label: 'Entrevistas', icon: Video },
  { id: 'mensajes', label: 'Mensajes', icon: MessageSquare },
]

function NavList({ current }: { current: string }) {
  return (
    <ul className="flex flex-col gap-1">
      {nav.map(({ id, label, icon: Icon }) => {
        const active = id === current
        return (
          <li key={id}>
            <a
              href={`#${id}`}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'flex items-center gap-3 rounded-full px-4 py-2.5 text-sm font-medium transition-colors focus-visible:outline-azure-300',
                active ? 'bg-white text-navy-900' : 'text-azure-200 hover:bg-white/10 hover:text-white',
              )}
            >
              <Icon className="size-4" aria-hidden="true" />
              {label}
            </a>
          </li>
        )
      })}
    </ul>
  )
}

export type ShellAccount = {
  /** Texto principal (hoy el correo: la API aún no guarda nombres). */
  name: string
  /** Rol en palabras, p. ej. "Reclutador/a". */
  detail: string
}

function UserCard({ account, onLogout }: { account?: ShellAccount; onLogout?: () => void }) {
  if (!account) return null
  const initials = account.name.replace(/[^a-zA-ZÀ-ÿ]/g, '').slice(0, 2).toUpperCase()
  return (
    <div className="flex items-center gap-3 rounded-card bg-white/5 p-3">
      <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-full bg-azure-500 text-xs font-semibold text-white">
        {initials}
      </span>
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-white" title={account.name}>
          {account.name}
        </p>
        <p className="truncate text-xs text-azure-200">{account.detail}</p>
      </div>
      {onLogout && (
        <button
          type="button"
          onClick={onLogout}
          className="ml-auto grid size-9 shrink-0 cursor-pointer place-items-center rounded-full text-azure-200 hover:bg-white/10 hover:text-white focus-visible:outline-azure-300"
        >
          <LogOut className="size-4" aria-hidden="true" />
          <span className="sr-only">Cerrar sesión</span>
        </button>
      )}
    </div>
  )
}

/** Estructura de la app: barra lateral navy en escritorio y barra superior con menú en móvil. */
type AppShellProps = {
  current: string
  account?: ShellAccount
  onLogout?: () => void
  children: ReactNode
}

export function AppShell({ current, account, onLogout, children }: AppShellProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_minmax(0,1fr)]">
      <a
        href="#contenido"
        className="sr-only z-50 rounded-full bg-white px-4 py-2 text-sm font-semibold text-navy-900 focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Saltar al contenido
      </a>

      {/* Escritorio */}
      <aside className="sticky top-0 hidden h-dvh flex-col gap-8 overflow-y-auto bg-navy-900 p-5 lg:flex">
        <div className="px-2 pt-1">
          <Logo />
        </div>
        <nav aria-label="Principal">
          <NavList current={current} />
        </nav>
        <div className="mt-auto">
          <UserCard account={account} onLogout={onLogout} />
        </div>
      </aside>

      {/* Móvil y tableta */}
      <header
        className="sticky top-0 z-40 bg-navy-900 lg:hidden"
        onKeyDown={(e) => {
          if (e.key === 'Escape' && menuOpen) {
            setMenuOpen(false)
            menuButton.current?.focus()
          }
        }}
      >
        <div className="flex h-14 items-center justify-between px-4">
          <Logo />
          <button
            ref={menuButton}
            type="button"
            aria-expanded={menuOpen}
            aria-controls="menu-movil"
            onClick={() => setMenuOpen((v) => !v)}
            className="grid size-10 cursor-pointer place-items-center rounded-full text-white hover:bg-white/10 focus-visible:outline-azure-300"
          >
            {menuOpen ? <X className="size-5" aria-hidden="true" /> : <Menu className="size-5" aria-hidden="true" />}
            <span className="sr-only">{menuOpen ? 'Cerrar menú' : 'Abrir menú'}</span>
          </button>
        </div>
        {menuOpen && (
          <nav id="menu-movil" aria-label="Principal" className="flex max-h-[calc(100dvh-3.5rem)] flex-col gap-4 overflow-y-auto overscroll-contain border-t border-white/10 px-4 pt-3 pb-5">
            <NavList current={current} />
            <UserCard account={account} onLogout={onLogout} />
          </nav>
        )}
      </header>

      <main id="contenido" tabIndex={-1} className="min-w-0 focus:outline-none">
        {children}
      </main>
    </div>
  )
}
