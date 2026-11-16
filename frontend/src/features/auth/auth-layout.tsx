import type { ReactNode } from 'react'
import { Logo } from '@/components/logo'

/** Estrellas del panel: posiciones fijas (no aleatorias) para que el diseño sea estable. */
const stars = [
  { top: '9%', left: '58%', size: 2 },
  { top: '16%', left: '81%', size: 1 },
  { top: '24%', left: '34%', size: 1 },
  { top: '31%', left: '72%', size: 2 },
  { top: '38%', left: '18%', size: 1 },
  { top: '44%', left: '88%', size: 1 },
  { top: '12%', left: '22%', size: 1 },
  { top: '52%', left: '62%', size: 1 },
]

/**
 * Pantallas de acceso (iniciar sesión, crear cuenta, recuperar y restablecer contraseña).
 * Escritorio: panel ilustrado a la izquierda (cielo nocturno en navy y azul) y el formulario a la derecha,
 * todo sobre navy profundo. Móvil: logo y formulario.
 *
 * El contenido vive en una "superficie oscura" (`data-surface="dark"`): Field, PasswordField, AppLink y
 * ProviderButton cambian sus colores para mantener el contraste AA sobre navy.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-navy-950 p-3 lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)] lg:gap-3">
      <aside
        className="relative hidden overflow-hidden rounded-panel lg:sticky lg:top-3 lg:flex lg:h-[calc(100dvh-1.5rem)] lg:flex-col lg:justify-between lg:p-12 lg:pb-[calc(20%+2rem)]"
        style={{
          background:
            'radial-gradient(90% 60% at 0% 58%, rgb(61 106 224 / 0.55), transparent 62%), radial-gradient(60% 40% at 100% 0%, rgb(38 70 140 / 0.45), transparent 70%), linear-gradient(180deg, #071330 0%, #0b1f4b 58%, #12295f 100%)',
        }}
      >
        <div aria-hidden="true" className="pointer-events-none absolute inset-0">
          {stars.map((s, i) => (
            <span
              key={i}
              className="absolute rounded-full bg-white/70"
              style={{ top: s.top, left: s.left, width: s.size, height: s.size }}
            />
          ))}
          <svg className="absolute inset-x-0 bottom-0 h-[20%] w-full" viewBox="0 0 800 300" preserveAspectRatio="none">
            <path d="M0 150 C 180 90 320 120 460 170 S 700 210 800 160 L 800 300 L 0 300 Z" fill="#12295f" opacity="0.85" />
            <path d="M0 210 C 160 160 300 190 430 225 S 660 250 800 205 L 800 300 L 0 300 Z" fill="#0b1f4b" />
            <path d="M0 210 C 160 160 300 190 430 225 S 660 250 800 205" fill="none" stroke="#a3bcf3" strokeOpacity="0.25" strokeWidth="1.5" />
            <path d="M0 255 C 200 225 380 250 560 270 S 720 268 800 250 L 800 300 L 0 300 Z" fill="#071330" />
          </svg>
        </div>

        <Logo className="relative" />
        <div className="relative max-w-md">
          <p className="text-5xl leading-[1.08] font-light tracking-tight text-white">
            Tu próximo trabajo.
            <br />
            Tu próximo <span className="font-semibold">equipo</span>.
          </p>
          <p className="mt-5 text-base leading-relaxed text-azure-200">
            Busca vacantes y postula en minutos, o publica las tuyas y encuentra a quien necesitas. Cada proceso, claro
            de principio a fin.
          </p>
        </div>
      </aside>

      <main
        data-surface="dark"
        className="group/surface flex min-h-[calc(100dvh-1.5rem)] flex-col px-2 py-6 text-white sm:px-6 lg:min-h-0 lg:justify-center lg:py-10"
      >
        <div className="lg:hidden">
          <Logo />
        </div>
        <div className="mx-auto mt-10 w-full max-w-sm lg:mt-0">{children}</div>
      </main>
    </div>
  )
}
