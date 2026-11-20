import { Info } from 'lucide-react'
import { useContext } from 'react'
import { ApplicationUpdatesContext } from '@/features/application/application-updates-context'
import { ApplicationUpdateNotice } from '@/features/application/application-update-notice'
import { InterviewInboxContext } from '@/features/interviews/interview-inbox-context'
import { InterviewNotice } from '@/features/interviews/interview-notice'

/** Las fallas de avisos comparten una franja; los errores de la página siguen en su contenido. */
export function WorkspaceNotices() {
  const updates = useContext(ApplicationUpdatesContext)
  const interviews = useContext(InterviewInboxContext)
  const applicationFailed = updates?.state.status === 'error'
  const interviewFailed = interviews?.failed
  const failed = applicationFailed || interviewFailed
  const hasUpdates = !!updates?.unread || !!interviews?.unread
  if (!failed && !hasUpdates) return null
  return <aside aria-label="Avisos de la cuenta" className="flex w-full max-w-[1440px] flex-col gap-2 px-4 pt-4 sm:px-6">
    {failed && <div role="status" className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-mid-700/20 bg-mid-100 px-4 py-3 text-sm text-ink-700">
      <Info className="size-4 shrink-0" aria-hidden="true" />
      <p className="min-w-0 flex-1">No pudimos actualizar los avisos de {applicationFailed && interviewFailed ? 'postulaciones y entrevistas' : applicationFailed ? 'postulaciones' : 'entrevistas'}. Consulta cada sección para comprobar sus datos.</p>
      <button className="min-h-8 cursor-pointer rounded-md px-2 font-semibold text-navy-900 underline underline-offset-2" onClick={() => {
        if (applicationFailed) updates?.refresh()
        if (interviewFailed) interviews?.refresh()
      }}>Reintentar avisos</button>
    </div>}
    <ApplicationUpdateNotice showError={false} />
    <InterviewNotice showError={false} />
  </aside>
}
