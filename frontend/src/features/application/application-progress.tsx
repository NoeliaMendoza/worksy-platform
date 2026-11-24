import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import { Notice } from '@/components/ui/notice'
import { useResource } from '@/lib/use-resource'
import { errorMessage } from '@/lib/http'
import { ApiError } from '@/features/auth/api'
import { applicationsApi, type ApplicationStage } from './api'
import { stageLabel } from './stage-labels'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'

const transitions: Record<ApplicationStage, ApplicationStage[]> = {
  RECEIVED: ['REVIEW', 'REJECTED'], REVIEW: ['INTERVIEW', 'REJECTED'],
  INTERVIEW: ['OFFER', 'REJECTED'], OFFER: ['HIRED', 'REJECTED'], HIRED: [], REJECTED: [],
}
const dateTime = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })

type Props = {
  token: string
  applicationId: string
  vacancyId?: string
  label: string
  onStageChanged?: () => void
  actions?: ReactNode
  initiallyExpanded?: boolean
}

export function ApplicationProgressPanel(props: Props) {
  const [expanded, setExpanded] = useState(props.initiallyExpanded ?? false)
  const id = `progress-${props.applicationId}`
  return (
    <div className="px-5 pb-4 sm:px-6">
      <div className="flex flex-wrap items-center gap-2">
      <Button variant="secondary" size="sm" aria-expanded={expanded} aria-controls={id}
        aria-label={`Seguimiento de ${props.label}`} onClick={() => setExpanded(!expanded)}>
        {expanded ? 'Ocultar seguimiento' : 'Ver seguimiento'}
      </Button>
      {props.actions}
      </div>
      {expanded && <div id={id} className="mt-3 rounded-2xl bg-azure-50 p-4"><ProgressDetails {...props} /></div>}
    </div>
  )
}

function ProgressDetails({ token, applicationId, vacancyId, onStageChanged }: Props) {
  const connectionStatus = useRealtime()?.status
  const { state, retry, refresh, replace } = useResource(
    () => applicationsApi.progress(token, applicationId, vacancyId),
    `progress:${applicationId}:${vacancyId ?? 'mine'}:${token}`, 'No se pudo cargar el seguimiento.',
  )
  const [next, setNext] = useState<ApplicationStage | ''>('')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState<{ tone: 'error' | 'success'; text: string } | null>(null)
  const busy = useRef(false)
  useEffect(() => {
    if (connectionStatus === 'online') refresh()
  }, [connectionStatus, refresh])
  useRealtimeNotices((notice) => {
    if (notice.type === 'reconnected' || (notice.type === 'application.stage.changed' && notice.applicationId === applicationId)) refresh()
  })

  async function save() {
    if (busy.current || state.status !== 'ready' || !vacancyId || !next) return
    busy.current = true
    setSaving(true)
    setFeedback(null)
    try {
      const result = await applicationsApi.moveStage(token, vacancyId, applicationId, state.data.stage, next, message)
      replace(result)
      setNext('')
      setMessage('')
      setFeedback({ tone: 'success', text: 'Etapa actualizada. El candidato puede verla en sus postulaciones.' })
      onStageChanged?.()
    } catch (err) {
      setFeedback({ tone: 'error', text: errorMessage(err, 'No se pudo confirmar el cambio. Actualiza el seguimiento antes de intentarlo de nuevo.') })
      // También cubre una respuesta perdida: consultamos la etapa sin repetir automáticamente el cambio.
      refresh()
      onStageChanged?.()
      if (err instanceof ApiError && err.status === 409) setNext('')
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  if (state.status === 'loading') return <p role="status">Cargando seguimiento…</p>
  if (state.status === 'error') return <Notice tone="error">{state.message} <button onClick={retry} className="underline">Reintentar</button></Notice>
  const choices = transitions[state.data.stage]
  const selectId = `stage-${applicationId}`
  return (
    <div className="flex flex-col gap-4">
      <p className="font-semibold text-ink-900">Etapa actual: {stageLabel[state.data.stage]}</p>
      <ol aria-label="Historial de etapas" className="flex flex-col gap-3">
        {state.data.history.map((event) => (
          <li key={event.id} className="border-l-2 border-azure-200 pl-3 text-sm">
            <p className="font-semibold">{stageLabel[event.stage]}</p>
            <time dateTime={event.changedAt} className="text-ink-500">{dateTime.format(new Date(event.changedAt))}</time>
            {event.message && <p className="mt-1 whitespace-pre-wrap break-words text-ink-700">{event.message}</p>}
          </li>
        ))}
      </ol>
      {!vacancyId && state.data.history.some((event) => event.fromStage !== null) && (
        <ReadProgress key={state.data.history.at(-1)?.id} token={token} applicationId={applicationId}
          lastEventId={state.data.history.at(-1)!.id} />
      )}
      <Button variant="secondary" size="sm" onClick={refresh} disabled={saving}>Actualizar seguimiento</Button>
      {vacancyId && choices.length > 0 && (
        <form className="flex flex-col gap-3 border-t border-azure-200 pt-4" onSubmit={(e) => { e.preventDefault(); void save() }}>
          <Field id={selectId} label="Nueva etapa">
            <select id={selectId} value={choices.includes(next as ApplicationStage) ? next : ''}
              onChange={(e) => setNext(e.target.value as ApplicationStage | '')} disabled={saving} required
              className="rounded-xl bg-white p-3 ring-1 ring-ink-300">
              <option value="">Selecciona una etapa</option>
              {choices.map((stage) => <option key={stage} value={stage}>{stageLabel[stage]}</option>)}
            </select>
          </Field>
          <Field id={`${selectId}-message`} label="Mensaje para el candidato (opcional)">
            <Textarea id={`${selectId}-message`} value={message} onChange={(e) => setMessage(e.target.value)}
              maxLength={500} disabled={saving} aria-describedby={`${selectId}-visibility`} />
          </Field>
          <p id={`${selectId}-visibility`} className="text-sm text-ink-700">Este mensaje será visible para el candidato. No incluyas notas internas. Máximo 500 caracteres.</p>
          <Button type="submit" disabled={saving || !next || !choices.includes(next)}>{saving ? 'Guardando…' : 'Guardar etapa'}</Button>
        </form>
      )}
      {vacancyId && choices.length === 0 && <p className="text-sm text-ink-700">Este proceso está finalizado.</p>}
      {feedback && <Notice tone={feedback.tone}>{feedback.text}</Notice>}
    </div>
  )
}

/** La lectura se confirma después de mostrar el historial y solo con la pestaña visible. */
function ReadProgress({ token, applicationId, lastEventId }: { token: string; applicationId: string; lastEventId: number }) {
  const [attempt, setAttempt] = useState(0)
  const [failed, setFailed] = useState(false)
  const emit = useRealtime()?.emit
  useEffect(() => {
    let active = true
    let sending = false
    let completed = false
    function mark() {
      if (!active || completed || sending || document.visibilityState !== 'visible') return
      sending = true
      applicationsApi.markProgressRead(token, applicationId, lastEventId).then(() => {
        if (!active) return
        completed = true
        setFailed(false)
        emit?.({ type: 'application.progress.read', applicationId })
      }, () => {
        sending = false
        if (active) setFailed(true)
      })
    }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => { active = false; document.removeEventListener('visibilitychange', mark) }
  }, [token, applicationId, lastEventId, attempt, emit])
  return failed ? <Notice tone="error">El seguimiento está disponible, pero no pudimos marcarlo como leído.
    <button className="ml-1 underline" onClick={() => setAttempt((n) => n + 1)}>Reintentar marcar como leído</button>
  </Notice> : null
}
