import { Briefcase, Search } from 'lucide-react'
import { navigate } from '@/lib/router'
import { cn } from '@/lib/cn'

const options = [
  { href: '/registro', label: 'Busco empleo', icon: Search },
  { href: '/registro-empresa', label: 'Soy una empresa', icon: Briefcase },
] as const

/**
 * Elige el tipo de cuenta al registrarse. Son enlaces (cada tipo tiene su propia página),
 * con aria-current en el actual. Los reclutadores no se registran aquí: los invita su empresa.
 */
export function AccountTypeSwitch({ current }: { current: '/registro' | '/registro-empresa' }) {
  return (
    <nav aria-label="Tipo de cuenta" className="mt-6">
      <ul className="grid grid-cols-2 gap-1 rounded-full bg-white/5 p-1 ring-1 ring-white/10">
        {options.map(({ href, label, icon: Icon }) => {
          const active = href === current
          return (
            <li key={href}>
              <a
                href={href}
                aria-current={active ? 'page' : undefined}
                onClick={(e) => {
                  if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
                  e.preventDefault()
                  navigate(href, { replace: true })
                }}
                className={cn(
                  'flex h-10 items-center justify-center gap-2 rounded-full text-sm font-semibold transition-colors focus-visible:outline-azure-300',
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
    </nav>
  )
}
