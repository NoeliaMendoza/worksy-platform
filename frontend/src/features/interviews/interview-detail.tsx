import { ArrowLeft, CalendarPlus, ExternalLink, Video as VideoIcon } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { AppLink } from '@/components/app-link'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/features/auth/api'
import { errorMessage } from '@/lib/http'
import { useRealtime } from '@/lib/realtime-context'
import { useResource } from '@/lib/use-resource'
import { downloadCalendarFile, interviewsApi, type Interview, type InterviewItem } from './api'
import { InterviewForm } from './interview-form'
import { historyLine, interviewState } from './interview-labels'
import { clock, dayKey, longWhen, otherZoneNote, relativeWhen, toInstant, viewerZone } from './interview-time'

type Panel = null | 'decline' | 'propose' | 'cancel' | 'edit' | 'reject'

/** Horario de la sala de videollamada (igual que la API): 15 min antes y hasta 30 min después del final. */
const ROOM_OPENS_BEFORE_MS = 15 * 60_000
const ROOM_CLOSES_AFTER_MS = 30 * 60_000

/** Ventana para corregir el resultado registrado (igual que la API). */
const OUTCOME_CORRECTION_MS = 24 * 3_600_000

export function InterviewDetail({ token, company, id, explicit, overlap, onChanged, onBack }: {
  token: string
  company: boolean
  id: string
  /** La persona la abrió (no es la selección automática): solo entonces se marca como leída. */
  explicit: boolean
  /** Otra cita propia del candidato que se cruza con esta (solo la ve el candidato). */
  overlap?: InterviewItem | null
  onChanged: () => void
  onBack?: () => void
}) {
  const [attempt, setAttempt] = useState(0)
  const { state, update } = useResource(() => interviewsApi.get(token, id), `${token}:${id}:${attempt}`, 'No se pudo cargar la entrevista.')
  const realtime = useRealtime()
  const [panel, setPanel] = useState<Panel>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [stale, setStale] = useState(false)
  const [downloading, setDownloading] = useState(false)
  // Hora actual para saber si la cita ya empezó; se actualiza sola cada 30 s.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  // Al cambiar de cita, se cierra lo que hubiera abierto.
  const [shownId, setShownId] = useState(id)
  if (shownId !== id) {
    setShownId(id)
    setPanel(null)
    setMessage(null)
    setStale(false)
  }

  // Recarga en segundo plano (sin volver a «Cargando»): tras una acción o un aviso en vivo.
  const refetch = useCallback(() => {
    interviewsApi.get(token, id).then((fresh) => update(() => fresh), () => undefined)
  }, [token, id, update])

  useEffect(() => {
    return realtime?.listen((n) => {
      if ((n.type === 'interview.changed' && n.interviewId === id) || n.type === 'reconnected') refetch()
    })
  }, [realtime, id, refetch])

  const item = state.status === 'ready' ? state.data : null
  useMarkRead(token, item, explicit)

  async function act(run: () => Promise<Interview>, success: string) {
    if (busy || stale) return
    setBusy(true)
    setMessage(null)
    try {
      const value = await run()
      update((current) => ({ ...current, interview: value, unread: false }))
      setPanel(null)
      setMessage({ tone: 'success', text: success })
      realtime?.emit({ type: 'interview.changed', interviewId: value.id, applicationId: value.applicationId })
      refetch()
      onChanged()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setStale(true)
        setMessage({ tone: 'error', text: err.code === 'interview.overlap' ? err.message
          : 'La entrevista cambió mientras respondías y tu acción no se guardó. Revisa la versión actual.' })
      } else {
        setMessage({ tone: 'error', text: errorMessage(err, 'No se pudo guardar. Revisa tu conexión e inténtalo de nuevo.') })
      }
    } finally {
      setBusy(false)
    }
  }

  async function addToCalendar() {
    setDownloading(true)
    try {
      await downloadCalendarFile(token, id)
      setMessage({ tone: 'success', text: 'Descargamos el archivo de la cita. Ábrelo para agregarla a tu calendario; si la cita cambia, descárgalo de nuevo y se actualizará.' })
    } catch (err) {
      setMessage({ tone: 'error', text: err instanceof Error ? err.message : 'No se pudo preparar el archivo del calendario.' })
    } finally {
      setDownloading(false)
    }
  }

  function reload() {
    setStale(false)
    setMessage(null)
    setPanel(null)
    refetch()
    onChanged()
  }

  if (state.status === 'loading') return <DetailFrame onBack={onBack}><p role="status" className="text-sm text-ink-700">Cargando la entrevista…</p></DetailFrame>
  if (state.status === 'error') return <DetailFrame onBack={onBack}>
    <p role="alert" className="text-sm text-ink-700">{state.message}</p>
    <Button variant="secondary" className="self-start" onClick={() => setAttempt((a) => a + 1)}>Intentar de nuevo</Button>
  </DetailFrame>

  const data = state.data
  const i = data.interview
  const view = interviewState(data, company)
  const future = Date.parse(i.startsAt) > now
  const changeable = future && ['INVITED', 'CONFIRMED', 'DECLINED'].includes(i.status)
  const request = data.changeRequest ?? null
  const correctable = (i.status === 'COMPLETED' || i.status === 'NO_SHOW') && !!i.outcomeAt && now - Date.parse(i.outcomeAt) < OUTCOME_CORRECTION_MS
  const canRecordOutcome = company && (data.phase === 'AWAITING_OUTCOME' || data.phase === 'EXPIRED' || correctable)
  const zoneNote = otherZoneNote(i.startsAt, i.zoneId)
  const roomOpens = Date.parse(i.startsAt) - ROOM_OPENS_BEFORE_MS
  const roomCloses = Date.parse(i.startsAt) + i.durationMinutes * 60_000 + ROOM_CLOSES_AFTER_MS
  const roomOpen = now >= roomOpens && now < roomCloses
  const v = i.version

  return <DetailFrame onBack={onBack}>
    <div className="flex flex-col items-start gap-1.5">
      <Badge tone={view.tone}>{view.label}</Badge>
      <h2 className="mt-1 break-words text-xl font-semibold text-navy-900">{i.title}</h2>
      <p className="text-sm text-ink-700">{company ? data.candidateEmail : data.companyName}, {data.vacancyTitle}</p>
    </div>

    {message && <Notice tone={message.tone} autoFocus>{message.text}</Notice>}
    {stale && <Button variant="secondary" className="self-start" onClick={reload}>Ver la versión actual</Button>}

    {request && !company && <p className="rounded-lg bg-azure-50 px-4 py-3 text-sm leading-relaxed text-navy-800">
      Propusiste {request.slots.length === 1 ? 'otra hora' : `${request.slots.length} horarios`}. La cita sigue en pie con la hora actual hasta que la empresa responda.
    </p>}
    {i.status === 'CANCELLED' && i.cancelReason && <p className="rounded-lg bg-low-100 px-4 py-3 text-sm text-low-700">Motivo de la cancelación: {i.cancelReason}</p>}
    {data.phase === 'EXPIRED' && <p className="rounded-lg bg-mid-100 px-4 py-3 text-sm text-mid-700">La hora pasó sin una confirmación.</p>}

    <dl className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-2.5 text-sm leading-relaxed">
      <dt className="text-ink-500">Cuándo</dt>
      <dd className="font-semibold text-ink-900">{longWhen(i.startsAt, i.durationMinutes)}
        {zoneNote && <span className="block font-normal text-ink-500">Para la empresa: {zoneNote}</span>}</dd>
      <dt className="text-ink-500">Dónde</dt>
      <dd className="break-words">{i.mode === 'ONLINE' ? 'Reunión en línea con enlace' : i.mode === 'VIDEO' ? 'Videollamada en Worksy (no se graba)' : i.place}</dd>
      {company && data.organizerEmail && <><dt className="text-ink-500">Responsable</dt><dd className="break-words">{data.organizerEmail}</dd></>}
      {i.note && <><dt className="text-ink-500">Mensaje</dt><dd className="whitespace-pre-wrap break-words">{i.note}</dd></>}
    </dl>

    {overlap && !company && future && <p className="rounded-lg bg-mid-100 px-4 py-3 text-sm text-mid-700">
      Ese día tienes otra entrevista que se cruza: {overlap.interview.title}, {clock(overlap.interview.startsAt)}. Solo tú ves este aviso.
    </p>}

    {i.mode === 'VIDEO' && ['INVITED', 'CONFIRMED'].includes(i.status) && (roomOpen
      ? <Button asChild><AppLink href={`/entrevistas/${i.id}/sala`} className="text-white hover:text-white hover:no-underline"><VideoIcon aria-hidden="true" />Entrar a la sala</AppLink></Button>
      : now < roomCloses && <p className="rounded-lg bg-azure-50 px-4 py-3 text-sm text-navy-800">La sala de videollamada abre 15 minutos antes, a las {clock(new Date(roomOpens))}.</p>)}

    {/* Acciones del candidato */}
    {!company && changeable && panel === null && <div className="flex flex-col gap-2">
      {i.status === 'INVITED' && <Button disabled={busy || stale} onClick={() => void act(() => interviewsApi.respond(token, i.id, v, 'CONFIRMED'), 'Confirmaste tu asistencia.')}>Confirmar asistencia</Button>}
      {i.status === 'CONFIRMED' && i.mode === 'ONLINE' && <Button asChild><a href={i.place} target="_blank" rel="noopener noreferrer">Abrir el enlace de la reunión<ExternalLink aria-hidden="true" /></a></Button>}
      <div className="flex flex-wrap gap-2">
        {request ? <Button variant="secondary" className="flex-1" disabled={busy || stale}
          onClick={() => void act(() => interviewsApi.withdrawChange(token, i.id, v, request.id), 'Retiraste tu propuesta. La hora actual sigue en pie.')}>Retirar mi propuesta</Button>
          : i.status !== 'DECLINED' && <Button variant="secondary" className="flex-1" disabled={busy || stale} onClick={() => setPanel('propose')}>Proponer otra hora</Button>}
        {i.status !== 'DECLINED' && <Button variant="secondary" className="flex-1 text-low-700" disabled={busy || stale} onClick={() => setPanel('decline')}>
          {i.status === 'CONFIRMED' ? 'Ya no puedo asistir' : 'No puedo asistir'}</Button>}
      </div>
    </div>}
    {!company && panel === 'decline' && <ReasonForm tone="danger" busy={busy} title="La empresa recibirá tu aviso y podrá proponerte otra hora."
      label="Motivo (opcional)" submit="Avisar que no asistiré" onCancel={() => setPanel(null)}
      onSubmit={(reason) => void act(() => interviewsApi.respond(token, i.id, v, 'DECLINED', reason), 'Avisaste que no puedes asistir.')} />}
    {!company && panel === 'propose' && <ProposeForm busy={busy} current={i} onCancel={() => setPanel(null)}
      onSubmit={(requestId, reason, slots) => void act(() => interviewsApi.requestChange(token, i.id, v, requestId, reason, slots), 'Enviamos tu propuesta a la empresa.')} />}

    {/* Acciones de la empresa */}
    {company && request && panel === null && changeable && <ChangeDecision request={request} busy={busy || stale}
      onAccept={(slot) => void act(() => interviewsApi.acceptChange(token, i.id, v, request.id, slot), 'Aceptaste la nueva hora. La entrevista quedó confirmada.')}
      onKeep={() => setPanel('reject')} />}
    {company && panel === 'reject' && request && <ReasonForm busy={busy} title="Se mantendrá la hora actual." label="Mensaje para la persona (opcional)"
      submit="Mantener la hora actual" onCancel={() => setPanel(null)}
      onSubmit={(reason) => void act(() => interviewsApi.rejectChange(token, i.id, v, request.id, reason), 'Se mantuvo la hora actual.')} />}
    {company && changeable && panel === null && <div className="flex flex-wrap gap-2">
      <Button variant="secondary" disabled={busy || stale} onClick={() => setPanel('edit')}>Reprogramar o editar</Button>
      <Button variant="secondary" className="text-low-700" disabled={busy || stale} onClick={() => setPanel('cancel')}>Cancelar entrevista</Button>
    </div>}
    {company && panel === 'cancel' && <ReasonForm tone="danger" required busy={busy} title={`¿Cancelar la entrevista del ${longWhen(i.startsAt, i.durationMinutes).replace(/^./, (c) => c.toLowerCase())}?`}
      label="Motivo (lo verá la persona candidata)" submit="Cancelar entrevista" back="Conservarla" onCancel={() => setPanel(null)}
      onSubmit={(reason) => void act(() => interviewsApi.cancel(token, i.id, v, reason), 'Entrevista cancelada. Avisamos a la persona.')} />}
    {company && panel === 'edit' && <InterviewForm token={token} applicationId={i.applicationId} current={i} embedded
      onSaved={(value) => { update((c) => ({ ...c, interview: value })); setPanel(null); setMessage({ tone: 'success', text: 'Cambios guardados.' }); realtime?.emit({ type: 'interview.changed', interviewId: value.id, applicationId: value.applicationId }); refetch(); onChanged() }}
      onClose={() => setPanel(null)} />}
    {canRecordOutcome && panel === null && <section aria-label="Resultado" className="flex flex-col gap-2 rounded-lg bg-azure-50 p-4">
      <h3 className="text-sm font-semibold text-navy-900">{correctable ? 'Corregir el resultado' : '¿Se realizó la entrevista?'}</h3>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" disabled={busy || stale || i.status === 'COMPLETED'} onClick={() => void act(() => interviewsApi.outcome(token, i.id, v, 'COMPLETED'), 'Registraste la entrevista como realizada.')}>Se realizó</Button>
        {(i.status === 'CONFIRMED' || correctable) && <Button size="sm" variant="secondary" disabled={busy || stale || i.status === 'NO_SHOW'}
          onClick={() => void act(() => interviewsApi.outcome(token, i.id, v, 'NO_SHOW'), 'Registraste que la persona no asistió.')}>No asistió</Button>}
      </div>
      <p className="text-xs text-ink-700">Esto no cambia la etapa ni decide el proceso. Puedes corregirlo durante 24 horas.</p>
    </section>}

    <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm font-semibold">
      {future && ['INVITED', 'CONFIRMED', 'CANCELLED'].includes(i.status) && <button type="button" disabled={downloading}
        onClick={() => void addToCalendar()}
        className="inline-flex min-h-10 cursor-pointer items-center gap-2 text-azure-600 underline-offset-4 hover:text-navy-900 hover:underline disabled:opacity-60">
        <CalendarPlus className="size-4" aria-hidden="true" />{i.status === 'CANCELLED' ? 'Quitar de mi calendario' : 'Agregar a mi calendario'}</button>}
      <AppLink href={`/mensajes/${i.applicationId}`} className="inline-flex min-h-10 items-center">Ir a la conversación</AppLink>
      {!company && <AppLink href={`/postulaciones?postulacion=${encodeURIComponent(i.applicationId)}`} className="inline-flex min-h-10 items-center">Ver mi postulación</AppLink>}
    </div>

    <History token={token} interview={i} company={company} />
  </DetailFrame>
}

function DetailFrame({ children, onBack }: { children: ReactNode; onBack?: () => void }) {
  return <div className="flex min-w-0 flex-col gap-5">
    {onBack && <button type="button" onClick={onBack} className="inline-flex min-h-11 cursor-pointer items-center gap-2 self-start text-sm font-semibold text-azure-600 lg:hidden">
      <ArrowLeft className="size-4" aria-hidden="true" />Volver a la agenda</button>}
    {children}
  </div>
}

function useMarkRead(token: string, item: InterviewItem | null, explicit: boolean) {
  const realtime = useRealtime()
  const marked = useRef<string>('')
  const key = item ? `${item.interview.id}:${item.interview.version}` : ''
  useEffect(() => {
    if (!item || !explicit || !item.unread || marked.current === key) return
    let active = true
    function mark() {
      if (!active || document.visibilityState !== 'visible' || marked.current === key || !item) return
      marked.current = key
      interviewsApi.read(token, item.interview.id, item.interview.version).then(
        () => realtime?.emit({ type: 'interview.read', interviewId: item.interview.id }),
        () => { marked.current = '' },
      )
    }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => { active = false; document.removeEventListener('visibilitychange', mark) }
  }, [token, item, explicit, key, realtime])
}

function ReasonForm({ title, label, submit, back = 'Volver', required = false, tone, busy, onSubmit, onCancel }: {
  title: string; label: string; submit: string; back?: string; required?: boolean; tone?: 'danger'; busy: boolean
  onSubmit: (reason: string) => void; onCancel: () => void
}) {
  const id = useId()
  const [reason, setReason] = useState('')
  return <form className={`flex flex-col gap-3 rounded-lg p-4 ${tone === 'danger' ? 'bg-low-100' : 'bg-azure-50'}`}
    onSubmit={(e) => { e.preventDefault(); onSubmit(reason.trim()) }}>
    <p className={`text-sm font-semibold ${tone === 'danger' ? 'text-low-700' : 'text-navy-900'}`}>{title}</p>
    <label htmlFor={id} className="text-sm text-ink-700">{label}</label>
    <Input id={id} value={reason} required={required} minLength={required ? 3 : undefined} maxLength={300} onChange={(e) => setReason(e.target.value)} />
    <div className="flex flex-wrap gap-2">
      <Button type="submit" disabled={busy} className={tone === 'danger' ? 'bg-low-700 hover:bg-low-700/90' : undefined}>{busy ? 'Guardando…' : submit}</Button>
      <Button type="button" variant="ghost" onClick={onCancel}>{back}</Button>
    </div>
  </form>
}

function ProposeForm({ current, busy, onSubmit, onCancel }: {
  current: Interview; busy: boolean; onSubmit: (requestId: string, reason: string, slots: string[]) => void; onCancel: () => void
}) {
  const prefix = useId()
  // Mismo id mientras el formulario siga abierto: reintentar no crea dos propuestas.
  const requestId = useRef(crypto.randomUUID())
  const [slots, setSlots] = useState<string[]>([''])
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  function submit(event: FormEvent) {
    event.preventDefault()
    try {
      const chosen = slots.filter(Boolean).map(toInstant)
      if (chosen.length === 0) throw new Error('Elige al menos un horario.')
      if (chosen.some((s) => Date.parse(s) <= Date.now())) throw new Error('Elige horarios futuros.')
      setError('')
      onSubmit(requestId.current, reason.trim(), chosen)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Revisa los horarios.')
    }
  }
  return <form onSubmit={submit} className="flex flex-col gap-3 rounded-lg bg-azure-50 p-4">
    <p className="text-sm font-semibold text-navy-900">Propón hasta 3 horarios. La cita del {dayKey(current.startsAt) === dayKey(new Date()) ? 'día de hoy' : relativeWhen(current.startsAt).toLowerCase()} sigue en pie mientras tanto.</p>
    <p className="text-xs text-ink-700">Horas en tu zona: {viewerZone()}. Duración: {current.durationMinutes} minutos.</p>
    {slots.map((slot, index) => <div key={index} className="flex flex-col gap-1">
      <label htmlFor={`${prefix}-${index}`} className="text-sm text-ink-700">Horario {index + 1}</label>
      <Input id={`${prefix}-${index}`} type="datetime-local" required={index === 0} value={slot}
        onChange={(e) => setSlots((all) => all.map((s, n) => n === index ? e.target.value : s))} />
    </div>)}
    {slots.length < 3 && <Button type="button" variant="ghost" size="sm" className="self-start" onClick={() => setSlots((all) => [...all, ''])}>Agregar otro horario</Button>}
    <label htmlFor={`${prefix}-reason`} className="text-sm text-ink-700">Motivo (lo verá la empresa)</label>
    <Textarea id={`${prefix}-reason`} rows={2} required minLength={3} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />
    {error && <p role="alert" className="text-sm text-low-700">{error}</p>}
    <div className="flex flex-wrap gap-2">
      <Button type="submit" disabled={busy}>{busy ? 'Enviando…' : 'Enviar propuesta'}</Button>
      <Button type="button" variant="ghost" onClick={onCancel}>Volver</Button>
    </div>
  </form>
}

function ChangeDecision({ request, busy, onAccept, onKeep }: {
  request: NonNullable<InterviewItem['changeRequest']>; busy: boolean; onAccept: (slot: number) => void; onKeep: () => void
}) {
  const name = useId()
  const [slot, setSlot] = useState(0)
  const chosen = request.slots[slot]
  return <section aria-label="Propuesta de otra hora" className="flex flex-col gap-3 rounded-lg bg-azure-50 p-4">
    <h3 className="text-sm font-semibold text-navy-900">La persona pide mover la cita</h3>
    <p className="text-sm text-ink-700">«{request.reason}»</p>
    <fieldset className="flex flex-col gap-2">
      <legend className="pb-1 text-sm text-ink-500">Horas que propone</legend>
      {request.slots.map((s, index) => <label key={s} className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-lg bg-white px-3 text-sm font-semibold ring-inset ${slot === index ? 'ring-2 ring-azure-600' : 'ring-1 ring-ink-300'}`}>
        <input type="radio" name={name} checked={slot === index} onChange={() => setSlot(index)} className="size-4 accent-azure-600" />
        {relativeWhen(s)}
      </label>)}
    </fieldset>
    <Button disabled={busy || !chosen} onClick={() => onAccept(slot)}>Aceptar {chosen ? relativeWhen(chosen).toLowerCase() : ''}</Button>
    <Button variant="secondary" disabled={busy} onClick={onKeep}>Mantener la hora actual</Button>
  </section>
}

function History({ token, interview, company }: { token: string; interview: Interview; company: boolean }) {
  const { state, retry } = useResource(() => interviewsApi.history(token, interview.id), `${token}:${interview.id}:${interview.version}`, 'No se pudo cargar el historial.')
  return <section aria-label="Historial de la entrevista" className="border-t border-ink-100 pt-4">
    <h3 className="mb-2 text-sm font-semibold text-navy-900">Historial</h3>
    {state.status === 'loading' && <p role="status" className="text-sm text-ink-500">Cargando historial…</p>}
    {state.status === 'error' && <p className="text-sm text-ink-700">{state.message} <button className="cursor-pointer underline" onClick={retry}>Intentar de nuevo</button></p>}
    {state.status === 'ready' && <ol className="flex flex-col gap-2 text-sm leading-relaxed text-ink-700">
      {[...state.data].reverse().map((e) => {
        const previous = state.data.find((p) => p.version === e.version - 1)
        return <li key={e.version} className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-3">
          <time dateTime={e.changedAt} className="text-ink-500 tabular-nums">{relativeWhen(e.changedAt).replace(/^(Hoy|Mañana), /, '$1 ')}</time>
          <span>{historyLine(e, previous, company)}{e.reason && <span className="block text-ink-500">«{e.reason}»</span>}</span>
        </li>
      })}
    </ol>}
  </section>
}
