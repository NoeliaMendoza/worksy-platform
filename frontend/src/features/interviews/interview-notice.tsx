import { useContext } from 'react'
import { Button } from '@/components/ui/button'
import { navigate } from '@/lib/router'
import { InterviewInboxContext } from './interview-inbox-context'

export function InterviewNotice({ showError = true, embedded = false }: { showError?: boolean; embedded?: boolean }) {
  const inbox = useContext(InterviewInboxContext)
  if (!inbox || (!inbox.failed && inbox.unread === 0)) return null
  if (inbox.failed && !showError) return null
  return <div className={`flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm text-navy-900 ${embedded ? '' : 'rounded-card border border-azure-200 bg-white'}`} role="status">
    {inbox.failed ? <><p>No pudimos consultar los avisos de entrevistas.</p><button className="underline" onClick={inbox.refresh}>Volver a consultar entrevistas</button></>
      : <><p>{inbox.unread === 1 ? 'Tienes 1 entrevista con cambios sin leer.' : `Tienes ${inbox.unread} entrevistas con cambios sin leer.`}</p>
        <Button variant="ghost" size="sm" onClick={() => navigate('/entrevistas?vista=updates')}>Revisar entrevistas</Button></>}
  </div>
}
