import type { ReactNode } from 'react'
import { Logo } from '@/components/logo'

/**
 * Pantallas públicas (inicio de sesión, recuperar y restablecer contraseña):
 * fondo navy completo con formas suaves y una tarjeta blanca centrada, como los paneles
 * clínicos de referencia. En móvil la tarjeta ocupa el ancho con su margen.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative isolate flex min-h-dvh flex-col items-center overflow-hidden bg-navy-900 px-4 py-10 sm:justify-center sm:py-12">
      {/* Anillos decorativos: dan profundidad sin degradados ni imágenes. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute -top-40 -right-40 size-[34rem] rounded-full border-[5rem] border-navy-800" />
        <div className="absolute -bottom-56 -left-48 size-[30rem] rounded-full border-[4rem] border-navy-800" />
        <div className="absolute top-1/3 left-[12%] hidden size-3 rounded-full bg-azure-500/60 lg:block" />
        <div className="absolute right-[16%] bottom-1/4 hidden size-2 rounded-full bg-azure-300/50 lg:block" />
      </div>

      <Logo className="[&_span]:text-xl" />

      <main className="mt-8 w-full max-w-[26rem] rounded-panel bg-white p-5 shadow-lift sm:p-9">{children}</main>
    </div>
  )
}
