import { FileSearch, ListOrdered, UserCheck } from 'lucide-react'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { navigate } from '@/lib/router'

const steps = [
  {
    icon: ListOrdered,
    title: 'El modelo ordena',
    text: 'Tu CV se compara con los requisitos de la vacante para ordenar la revisión.',
  },
  {
    icon: UserCheck,
    title: 'Una persona decide',
    text: 'Quien recluta revisa tu CV y la evidencia. Ningún CV se descarta de forma automática.',
  },
]

/** Inicio de la persona candidata: sus postulaciones. Nunca muestra rankings ni datos de otras personas. */
export function CandidateHome() {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Tus <span className="font-semibold">postulaciones</span>
        </h1>
        <p className="mt-3 max-w-xl text-base text-ink-700">
          Aquí verás cada vacante a la que te postules y en qué etapa está.
        </p>
      </header>

      <StateMessage
        icon={FileSearch}
        title="Todavía no tienes postulaciones"
        description="Empieza por subir tu CV. Luego podrás postularte a las vacantes publicadas y seguir cada proceso desde aquí."
        action={<Button onClick={() => navigate('/mi-cv')}>Subir mi CV</Button>}
      />

      <section aria-labelledby="como-se-revisa" className="rounded-card bg-azure-100 p-6">
        <h2 id="como-se-revisa" className="text-base font-semibold text-navy-900">
          Cómo se revisa tu CV
        </h2>
        <ul className="mt-4 grid gap-4 sm:grid-cols-2">
          {steps.map(({ icon: Icon, title, text }) => (
            <li key={title} className="flex gap-3">
              <span className="grid size-9 shrink-0 place-items-center rounded-2xl bg-white text-navy-700">
                <Icon className="size-4" aria-hidden="true" />
              </span>
              <div>
                <p className="text-sm font-semibold text-ink-900">{title}</p>
                <p className="mt-0.5 text-sm leading-relaxed text-ink-700">{text}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
