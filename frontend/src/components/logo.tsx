import { cn } from '@/lib/cn'

type Tone = 'dark' | 'light'

/** Colores del símbolo según el fondo (tokens de la paleta). */
const markColors: Record<Tone, { lens: string; w: string; handle: string }> = {
  dark: { lens: 'stroke-azure-200', w: 'stroke-white', handle: 'stroke-azure-400' },
  light: { lens: 'stroke-navy-900', w: 'stroke-azure-500', handle: 'stroke-azure-500' },
}

/**
 * Símbolo de Worksy: una lupa con una W dentro del lente (búsqueda y selección).
 * Decorativo: el nombre accesible lo da el texto que lo acompaña.
 */
export function LogoMark({ tone = 'dark', className }: { tone?: Tone; className?: string }) {
  const c = markColors[tone]
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true" fill="none" strokeLinecap="round" className={cn('size-8 shrink-0', className)}>
      <circle cx="21" cy="21" r="15" strokeWidth="4" className={c.lens} />
      <path d="M13 17l3.5 9 4.5-7 4.5 7 3.5-9" strokeWidth="3" strokeLinejoin="round" className={c.w} />
      <path d="M32 32l10 10" strokeWidth="5" className={c.handle} />
    </svg>
  )
}

type LogoProps = {
  /** `dark`: sobre navy (texto blanco). `light`: sobre fondos claros (texto navy). */
  tone?: Tone
  className?: string
}

/** Logo completo: símbolo + "Worksy". */
export function Logo({ tone = 'dark', className }: LogoProps) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <LogoMark tone={tone} />
      <span className={cn('text-xl font-semibold tracking-tight', tone === 'dark' ? 'text-white' : 'text-navy-900')}>
        Worksy
      </span>
    </span>
  )
}
