import { Building2 } from 'lucide-react'
import { StateMessage } from '@/components/state-message'

/** Inicio de la administración de Worksy: empresas de la plataforma. */
export function PlatformAdminHome() {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Administración de <span className="font-semibold">Worksy</span>
        </h1>
        <p className="mt-3 max-w-xl text-base text-ink-700">Empresas, cuentas y registro de auditoría de la plataforma.</p>
      </header>
      <StateMessage
        icon={Building2}
        title="Todavía no hay empresas registradas"
        description="Las empresas que se registren en Worksy aparecerán aquí, con sus reclutadores y vacantes."
      />
    </div>
  )
}
