import { Construction } from 'lucide-react'
import { StateMessage } from '@/components/state-message'

/** Sección del menú que aún no existe: se dice con claridad en vez de mostrar otra pantalla. */
export function ComingSoon({ section }: { section: string }) {
  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-8 sm:py-10">
      <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">{section}</h1>
      <StateMessage
        icon={Construction}
        title="Esta sección todavía no está disponible"
        description="Llegará en una próxima versión de Worksy. Mientras tanto, usa las otras secciones del menú."
        className="mt-8"
      />
    </div>
  )
}
