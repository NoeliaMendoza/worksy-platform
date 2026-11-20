import { useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { ApiError } from '@/features/auth/api'
import { errorMessage } from '@/lib/http'
import { interviewsApi, type Interview, type InterviewDetails } from './api'
import { interviewTime, localDateTime, toInstant } from './interview-time'

export function InterviewForm({ token, applicationId, current, onSaved, onClose }: {
  token: string; applicationId: string; current?: Interview; onSaved: (value: Interview) => void; onClose: () => void
}) {
  const prefix = useId()
  const requestId = useRef(crypto.randomUUID())
  const pending = useRef<InterviewDetails | null>(null)
  const busy = useRef(false)
  const [sending, setSending] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [conflict, setConflict] = useState(false)
  const [error, setError] = useState('')
  const zoneId = Intl.DateTimeFormat().resolvedOptions().timeZone
  const [title, setTitle] = useState(current?.title ?? 'Entrevista de selección')
  const [date, setDate] = useState(current ? localDateTime(current.startsAt) : '')
  const [duration, setDuration] = useState(current?.durationMinutes ?? 30)
  const [mode, setMode] = useState<InterviewDetails['mode']>(current?.mode ?? 'ONLINE')
  const [place, setPlace] = useState(current?.place ?? '')
  const [note, setNote] = useState(current?.note ?? '')
  const dateValue = new Date(date)
  const preview = Number.isFinite(dateValue.getTime()) ? interviewTime(dateValue.toISOString(), zoneId) : null

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy.current || conflict) return
    busy.current = true
    setSending(true)
    setError('')
    try {
      const details = pending.current ?? { title, startsAt: toInstant(date), durationMinutes: duration, zoneId, mode, place, note }
      pending.current = details
      const saved = current ? await interviewsApi.update(token, current.id, current.version, details)
        : await interviewsApi.schedule(token, requestId.current, applicationId, details)
      onSaved(saved)
    } catch (err) {
      const ambiguous = err instanceof ApiError && (err.status === 0 || err.status >= 500)
      setUncertain(ambiguous)
      if (!ambiguous) pending.current = null
      setConflict(err instanceof ApiError && err.status === 409)
      setError(err instanceof Error && !(err instanceof ApiError) ? err.message : errorMessage(err, 'No se pudo guardar la entrevista.'))
    } finally {
      busy.current = false
      setSending(false)
    }
  }
  const id = (name: string) => `${prefix}-${name}`
  return <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 rounded-card bg-white p-5 shadow-soft">
    <h2 className="text-lg font-semibold">{current ? 'Reprogramar o editar entrevista' : 'Agendar entrevista'}</h2>
    <p className="text-sm text-ink-700">La fecha se introduce en tu zona: <strong>{zoneId}</strong>. La invitación aparece dentro de Worksy.</p>
    <p className="text-sm text-ink-500">Elige una fecha futura dentro de los próximos 365 días y una duración de 15 a 180 minutos.</p>
    {preview && <p className="text-sm" role="status">Hora que se enviará: {preview}</p>}
    {current && <p className="text-sm text-ink-700">Al guardar, la persona deberá confirmar de nuevo, aunque solo cambies el mensaje.</p>}
    <fieldset disabled={sending || uncertain || conflict} className="flex flex-col gap-4">
      <Field id={id('title')} label="Título"><Input id={id('title')} required maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      <Field id={id('date')} label="Fecha y hora"><Input id={id('date')} type="datetime-local" required value={date} onChange={(e) => setDate(e.target.value)} /></Field>
      <Field id={id('duration')} label="Duración (minutos)"><Input id={id('duration')} type="number" required min={15} max={180} value={duration} onChange={(e) => setDuration(Number(e.target.value))} /></Field>
      <Field id={id('mode')} label="Modalidad"><select id={id('mode')} className="rounded-xl border border-ink-200 p-3" value={mode} onChange={(e) => setMode(e.target.value as InterviewDetails['mode'])}><option value="ONLINE">En línea</option><option value="IN_PERSON">Presencial</option></select></Field>
      <Field id={id('place')} label={mode === 'ONLINE' ? 'Enlace HTTPS de la reunión' : 'Dirección del encuentro'}><Input id={id('place')} type={mode === 'ONLINE' ? 'url' : 'text'} pattern={mode === 'ONLINE' ? 'https://.*' : undefined} required maxLength={500} value={place} onChange={(e) => setPlace(e.target.value)} /></Field>
      <Field id={id('note')} label="Mensaje para la persona (opcional)"><textarea id={id('note')} className="rounded-xl border border-ink-200 p-3" maxLength={1000} rows={3} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
    </fieldset>
    <p className="text-sm text-ink-500">Todos estos datos son visibles para la persona y el equipo de la empresa. Crea la sala en tu servicio de reuniones y pega su enlace; Worksy no crea salas ni envía correos.</p>
    {error && <Notice tone="error">{error}</Notice>}
    {uncertain && <p role="status" className="text-sm">No sabemos si se guardó. Reintenta la misma solicitud o vuelve a la lista para comprobarlo.</p>}
    {conflict && <p role="status" className="text-sm">La cita cambió. Vuelve a la lista y abre su versión actual antes de editar.</p>}
    <div className="flex flex-wrap gap-3"><Button type="submit" disabled={sending || conflict}>{sending ? 'Guardando…' : uncertain ? 'Reintentar la misma solicitud' : 'Guardar invitación'}</Button>
      <Button type="button" variant="secondary" onClick={onClose}>Volver a entrevistas</Button></div>
  </form>
}
