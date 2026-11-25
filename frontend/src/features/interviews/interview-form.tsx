import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/features/auth/api'
import { cn } from '@/lib/cn'
import { errorMessage } from '@/lib/http'
import { interviewsApi, type Interview, type InterviewDetails, type InterviewOverlap } from './api'
import { interviewTime, localDateTime, relativeWhen, timeRange, toInstant, viewerZone } from './interview-time'

const selectClass = 'min-h-10 w-full rounded-md bg-white px-3 text-base text-ink-900 ring-1 ring-ink-300 ring-inset hover:ring-azure-400 focus-visible:ring-2 focus-visible:ring-azure-500 focus-visible:outline-none sm:text-sm'

/**
 * Agenda o reprograma una entrevista. Avisa (sin bloquear) si se cruza con otra cita a cargo de quien agenda;
 * la API sí bloquea dos citas de la misma postulación a la vez.
 */
export function InterviewForm({ token, applicationId, current, onSaved, onClose, embedded = false }: {
  token: string; applicationId: string; current?: Interview; onSaved: (value: Interview) => void; onClose: () => void
  /** Dentro del panel de detalle: sin tarjeta propia. */
  embedded?: boolean
}) {
  const prefix = useId()
  const requestId = useRef(crypto.randomUUID())
  const pending = useRef<InterviewDetails | null>(null)
  const busy = useRef(false)
  const [sending, setSending] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [conflict, setConflict] = useState(false)
  const [error, setError] = useState('')
  const zoneId = current?.zoneId ?? viewerZone()
  const [title, setTitle] = useState(current?.title ?? 'Entrevista de selección')
  const [date, setDate] = useState(current ? localDateTime(current.startsAt) : '')
  const [duration, setDuration] = useState(current?.durationMinutes ?? 30)
  const [mode, setMode] = useState<InterviewDetails['mode']>(current?.mode ?? 'ONLINE')
  const [place, setPlace] = useState(current?.place ?? '')
  const [note, setNote] = useState(current?.note ?? '')
  const [reason, setReason] = useState('')
  const [overlaps, setOverlaps] = useState<InterviewOverlap[]>([])
  const dateValue = new Date(date)
  const validDate = Number.isFinite(dateValue.getTime())
  const preview = validDate ? interviewTime(dateValue.toISOString(), zoneId) : null
  const material = !!current && (!validDate || dateValue.toISOString() !== new Date(current.startsAt).toISOString()
    || duration !== current.durationMinutes || mode !== current.mode || place !== current.place)

  // Aviso de cruces: se consulta un momento después de elegir la hora.
  useEffect(() => {
    if (!validDate || duration < 15 || duration > 180) return
    let active = true
    const timer = window.setTimeout(() => {
      interviewsApi.overlaps(token, dateValue.toISOString(), duration, current?.id).then((found) => active && setOverlaps(found), () => active && setOverlaps([]))
    }, 400)
    return () => { active = false; window.clearTimeout(timer) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, date, duration, current?.id])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy.current || conflict) return
    busy.current = true
    setSending(true)
    setError('')
    try {
      const details = pending.current ?? { title, startsAt: toInstant(date), durationMinutes: duration, zoneId, mode, place, note }
      pending.current = details
      const saved = current ? await interviewsApi.update(token, current.id, current.version, details, reason.trim())
        : await interviewsApi.schedule(token, requestId.current, applicationId, details)
      onSaved(saved)
    } catch (err) {
      const ambiguous = err instanceof ApiError && (err.status === 0 || err.status >= 500)
      setUncertain(ambiguous)
      if (!ambiguous) pending.current = null
      setConflict(err instanceof ApiError && err.status === 409 && err.code === 'interview.conflict')
      setError(err instanceof Error && !(err instanceof ApiError) ? err.message : errorMessage(err, 'No se pudo guardar la entrevista.'))
    } finally {
      busy.current = false
      setSending(false)
    }
  }
  const id = (name: string) => `${prefix}-${name}`
  return <form onSubmit={(e) => void submit(e)} className={cn('flex flex-col gap-4', embedded ? 'rounded-lg bg-azure-50 p-4' : 'rounded-card bg-white p-5 shadow-soft')}>
    <h2 className="text-lg font-semibold text-navy-900">{current ? 'Reprogramar o editar' : 'Agendar entrevista'}</h2>
    <fieldset disabled={sending || uncertain || conflict} className="flex flex-col gap-4">
      <Field id={id('title')} label="Título"><Input id={id('title')} required maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      <div className="grid gap-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Field id={id('date')} label="Fecha y hora"><Input id={id('date')} type="datetime-local" required value={date} onChange={(e) => setDate(e.target.value)} /></Field>
        <Field id={id('duration')} label="Duración (minutos)"><Input id={id('duration')} type="number" required min={15} max={180} step={5} value={duration} onChange={(e) => setDuration(Number(e.target.value))} /></Field>
      </div>
      <p className="-mt-2 text-sm text-ink-500" role="status">{preview ? `Hora que se enviará: ${preview}` : `La hora se indica en ${zoneId}.`}</p>
      {overlaps.length > 0 && <p role="status" className="rounded-lg bg-mid-100 px-4 py-3 text-sm leading-relaxed text-mid-700">
        Se cruza con {overlaps.length === 1 ? 'otra entrevista tuya' : `${overlaps.length} entrevistas tuyas`}: {overlaps.map((o) => `${o.title} de ${o.vacancyTitle}, ${relativeWhen(o.startsAt).toLowerCase()} (${timeRange(o.startsAt, o.durationMinutes)})`).join('; ')}.
        Puedes guardar igual si la lleva otra persona del equipo.
      </p>}
      <Field id={id('mode')} label="Modalidad"><select id={id('mode')} className={selectClass} value={mode} onChange={(e) => setMode(e.target.value as InterviewDetails['mode'])}>
        <option value="ONLINE">Reunión en línea (enlace)</option><option value="IN_PERSON">Presencial</option></select></Field>
      <Field id={id('place')} label={mode === 'ONLINE' ? 'Enlace HTTPS de la reunión' : 'Dirección e indicaciones para llegar'}>
        <Input id={id('place')} type={mode === 'ONLINE' ? 'url' : 'text'} pattern={mode === 'ONLINE' ? 'https://.*' : undefined} required maxLength={500} value={place} onChange={(e) => setPlace(e.target.value)} /></Field>
      <Field id={id('note')} label="Mensaje para la persona (opcional)"><Textarea id={id('note')} maxLength={1000} rows={3} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      {current && <Field id={id('reason')} label="Motivo del cambio (opcional, lo verá la persona)"><Input id={id('reason')} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>}
    </fieldset>
    {current && <p className="text-sm text-ink-700">{material ? 'Al guardar, la persona deberá confirmar de nuevo y se cerrará cualquier propuesta de otra hora.' : 'Si solo cambias el título o el mensaje, se conserva su respuesta.'}</p>}
    <p className="text-xs text-ink-500">Todos estos datos son visibles para la persona y para tu equipo. Crea la reunión en tu servicio habitual y pega su enlace.</p>
    {error && <Notice tone="error">{error}</Notice>}
    {uncertain && <p role="status" className="text-sm">No sabemos si se guardó. Reintenta la misma solicitud o vuelve a la agenda para comprobarlo.</p>}
    {conflict && <p role="status" className="text-sm">La cita cambió. Vuelve a la agenda y abre su versión actual antes de editar.</p>}
    <div className="flex flex-wrap gap-3"><Button type="submit" disabled={sending || conflict}>{sending ? 'Guardando…' : uncertain ? 'Reintentar la misma solicitud' : current ? 'Guardar cambios' : 'Enviar invitación'}</Button>
      <Button type="button" variant="ghost" onClick={onClose}>{current ? 'Descartar cambios' : 'Volver a entrevistas'}</Button></div>
  </form>
}
