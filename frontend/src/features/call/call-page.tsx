import { Mic, MicOff, PhoneOff, Settings2, Video, VideoOff } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { AppLink } from '@/components/app-link'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { useAuth } from '@/features/auth/auth-context'
import { interviewsApi, type InterviewItem } from '@/features/interviews/api'
import { longWhen } from '@/features/interviews/interview-time'
import { cn } from '@/lib/cn'
import { useResource } from '@/lib/use-resource'
import { callApi } from './api'
import { useCallRoom, type RemotePeer } from './use-call-room'
import { MEDIA_PROBLEM, useLocalMedia } from './use-local-media'

const selectClass = 'min-h-11 w-full rounded-md bg-white px-3 text-sm text-ink-900 ring-1 ring-ink-300 ring-inset focus-visible:ring-2 focus-visible:ring-azure-500 focus-visible:outline-none'

export function CallPage({ interviewId }: { interviewId: string }) {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null
  return <CallRoomScreen token={state.accessToken} interviewId={interviewId} company={state.user.role !== 'CANDIDATE'} />
}

function CallRoomScreen({ token, interviewId, company }: { token: string; interviewId: string; company: boolean }) {
  const interview = useResource(() => interviewsApi.get(token, interviewId), `${token}:${interviewId}`, 'No se pudo cargar la entrevista.')
  const media = useLocalMedia()
  const room = useCallRoom({ token, interviewId, stream: media.stream, micOn: media.micOn, camOn: media.camOn })
  const [duration, setDuration] = useState(0)

  useEffect(() => {
    if (room.phase !== 'in-call' || room.joinedAt === null) return
    const timer = window.setInterval(() => setDuration(Math.floor((Date.now() - room.joinedAt!) / 1000)), 1000)
    return () => window.clearInterval(timer)
  }, [room.phase, room.joinedAt])

  // Al salir se apagan cámara y micrófono.
  const { stop } = media
  useEffect(() => { if (room.phase === 'ended') stop() }, [room.phase, stop])

  const item = interview.state.status === 'ready' ? interview.state.data : null
  if (interview.state.status === 'error') return <div className="app-page"><Notice tone="error">{interview.state.message}</Notice>
    <AppLink href="/entrevistas" className="inline-flex min-h-11 items-center self-start">Volver a Entrevistas</AppLink></div>
  if (!item) return <div className="app-page"><p role="status" className="text-sm text-ink-700">Preparando la sala…</p></div>

  if (room.phase === 'ended') return <Ended item={item} company={company} seconds={duration} onRejoin={() => { void media.retry().then(() => room.enter()) }} />
  if (room.phase === 'in-call') return <Room item={item} company={company} media={media} room={room} seconds={duration} />
  return <Prejoin token={token} item={item} company={company} media={media} joining={room.phase === 'joining'} error={room.error} onJoin={() => void room.enter()} />
}

function VideoTile({ stream, muted, className, label, sinkId }: { stream: MediaStream | null; muted?: boolean; className?: string; label: string; sinkId?: string }) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (ref.current && ref.current.srcObject !== stream) ref.current.srcObject = stream
  }, [stream])
  useEffect(() => {
    const video = ref.current as (HTMLVideoElement & { setSinkId?: (id: string) => Promise<void> }) | null
    if (sinkId && video?.setSinkId) void video.setSinkId(sinkId).catch(() => undefined)
  }, [sinkId])
  return <video ref={ref} autoPlay playsInline muted={muted} aria-label={label} className={cn('size-full object-cover', className)} />
}

type Media = ReturnType<typeof useLocalMedia>
type Room = ReturnType<typeof useCallRoom>

function DeviceFields({ media, speaker, onSpeaker }: { media: Media; speaker: string; onSpeaker: (id: string) => void }) {
  const id = useId()
  const { devices } = media
  const name = (d: MediaDeviceInfo, n: number, kind: string) => d.label || `${kind} ${n + 1}`
  return <div className="grid gap-4 sm:grid-cols-3">
    <label htmlFor={`${id}-mic`} className="flex flex-col gap-1.5 text-sm font-semibold text-navy-900">Micrófono
      <select id={`${id}-mic`} className={selectClass} value={media.micId} onChange={(e) => void media.chooseMic(e.target.value)} disabled={!devices.microphones.length}>
        {!media.micId && <option value="">Predeterminado</option>}
        {devices.microphones.map((d, n) => <option key={d.deviceId || n} value={d.deviceId}>{name(d, n, 'Micrófono')}</option>)}
      </select></label>
    <label htmlFor={`${id}-cam`} className="flex flex-col gap-1.5 text-sm font-semibold text-navy-900">Cámara
      <select id={`${id}-cam`} className={selectClass} value={media.camId} onChange={(e) => void media.chooseCam(e.target.value)} disabled={!devices.cameras.length}>
        {!media.camId && <option value="">Predeterminada</option>}
        {devices.cameras.map((d, n) => <option key={d.deviceId || n} value={d.deviceId}>{name(d, n, 'Cámara')}</option>)}
      </select></label>
    <label htmlFor={`${id}-out`} className="flex flex-col gap-1.5 text-sm font-semibold text-navy-900">Altavoz
      <select id={`${id}-out`} className={selectClass} value={speaker} onChange={(e) => onSpeaker(e.target.value)} disabled={!devices.speakers.length}>
        <option value="">Predeterminado</option>
        {devices.speakers.map((d, n) => <option key={d.deviceId || n} value={d.deviceId}>{name(d, n, 'Altavoz')}</option>)}
      </select></label>
  </div>
}

function Prejoin({ token, item, company, media, joining, error, onJoin }: {
  token: string; item: InterviewItem; company: boolean; media: Media; joining: boolean; error: string | null; onJoin: () => void
}) {
  const i = item.interview
  const [speaker, setSpeaker] = useState('')
  const status = useResource(() => callApi.status(token, i.id), `${token}:${i.id}:estado`, 'No se pudo consultar la sala.')
  const { refresh } = status
  useEffect(() => {
    const timer = window.setInterval(refresh, 15_000)
    return () => window.clearInterval(timer)
  }, [refresh])
  const s = status.state.status === 'ready' ? status.state.data : null
  const inside = s?.present ?? []
  return <div className="flex w-full max-w-[1440px] flex-col">
    <header className="flex flex-wrap items-center gap-x-6 gap-y-1 border-b border-ink-100 px-4 py-4 sm:px-8">
      <AppLink href={`/entrevistas?cita=${i.id}`} className="inline-flex min-h-11 items-center text-sm">Volver a Entrevistas</AppLink>
      <h1 className="text-xl font-bold text-navy-900">Prepárate para entrar</h1>
    </header>
    <div className="flex flex-wrap gap-8 px-4 py-7 sm:px-8">
      <section aria-label="Tu cámara y tu micrófono" className="flex min-w-0 flex-[999_1_30rem] flex-col gap-4">
        <div className="relative grid aspect-video place-items-center overflow-hidden rounded-2xl bg-navy-900">
          {media.stream && media.camOn && media.stream.getVideoTracks().length > 0
            ? <VideoTile stream={media.stream} muted label="Vista previa de tu cámara" className="-scale-x-100" />
            : <span className="text-sm text-azure-200">{media.problem ? 'Sin cámara' : media.stream ? 'Tu cámara está apagada' : 'Pidiendo permiso para la cámara…'}</span>}
          <div className="absolute inset-x-0 bottom-4 flex justify-center gap-3">
            <MediaButton on={media.micOn} onClick={media.toggleMic} disabled={!media.stream?.getAudioTracks().length} onLabel="Silenciar micrófono" offLabel="Micrófono silenciado" Icon={media.micOn ? Mic : MicOff} />
            <MediaButton on={media.camOn} onClick={media.toggleCam} disabled={!media.stream?.getVideoTracks().length} onLabel="Apagar cámara" offLabel="Cámara apagada" Icon={media.camOn ? Video : VideoOff} />
          </div>
        </div>
        {media.problem && <div role="alert" className="flex flex-col items-start gap-3 rounded-lg bg-mid-100 px-4 py-3 text-sm text-mid-700">
          <p>{MEDIA_PROBLEM[media.problem]}</p>
          <Button size="sm" variant="secondary" onClick={() => void media.retry()}>Volver a intentar</Button></div>}
        {media.lost && <p role="alert" className="rounded-lg bg-mid-100 px-4 py-3 text-sm text-mid-700">{media.lost}</p>}
        <DeviceFields media={media} speaker={speaker} onSpeaker={setSpeaker} />
      </section>
      <aside aria-label="Tu entrevista" className="flex flex-[1_1_20rem] flex-col gap-5">
        <div><h2 className="text-xl font-bold text-navy-900">{i.title}</h2>
          <p className="text-sm text-ink-700">{company ? item.candidateEmail : item.companyName}, {item.vacancyTitle}</p></div>
        <dl className="grid grid-cols-[6rem_minmax(0,1fr)] gap-x-3 gap-y-2 text-sm"><dt className="text-ink-500">Cuándo</dt><dd className="font-semibold">{longWhen(i.startsAt, i.durationMinutes)}</dd></dl>
        <ul className="flex flex-col gap-2.5 text-sm text-ink-900" aria-live="polite">
          <li className="flex gap-2.5"><Dot tone={media.problem ? 'mid' : media.stream ? 'high' : 'neutral'} />
            {media.problem ? 'Sin permiso de cámara o micrófono' : media.stream ? 'Cámara y micrófono listos' : 'Pidiendo permiso para la cámara y el micrófono'}</li>
          <li className="flex gap-2.5"><Dot tone={s?.joinable ? 'azure' : 'mid'} />
            {!s ? 'Comprobando la sala…' : !s.joinable ? s.unavailableReason : inside.length
              ? `La sala está abierta. Ya está dentro: ${inside.map((p) => p.label).join(', ')}.` : 'La sala está abierta. Todavía no entró nadie más.'}</li>
        </ul>
        {error && <Notice tone="error">{error}</Notice>}
        <Button className="min-h-12 text-[0.9375rem]" disabled={joining || !s?.joinable} onClick={onJoin}>{joining ? 'Entrando…' : 'Unirme a la entrevista'}</Button>
        <p className="text-[0.8125rem] leading-relaxed text-ink-500">Worksy no graba la llamada. El audio y el video van cifrados de un navegador al otro y no se guardan.</p>
      </aside>
    </div>
  </div>
}

function Dot({ tone }: { tone: 'high' | 'mid' | 'azure' | 'neutral' }) {
  const color = { high: 'bg-high-700', mid: 'bg-mid-700', azure: 'bg-azure-600', neutral: 'bg-ink-300' }[tone]
  return <span aria-hidden="true" className={cn('mt-1.5 size-2 shrink-0 rounded-full', color)} />
}

function MediaButton({ on, onClick, disabled, onLabel, offLabel, Icon, dark }: {
  on: boolean; onClick: () => void; disabled?: boolean; onLabel: string; offLabel: string; Icon: typeof Mic; dark?: boolean
}) {
  return <button type="button" onClick={onClick} disabled={disabled} aria-pressed={!on}
    className={cn('inline-flex min-h-12 cursor-pointer items-center gap-2 rounded-full px-5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50',
      on ? (dark ? 'bg-white text-navy-900' : 'bg-white/90 text-navy-900') : 'bg-low-700 text-white')}>
    <Icon className="size-4" aria-hidden="true" />{on ? onLabel : offLabel}</button>
}

function quality(peers: RemotePeer[], online: boolean): { text: string; tone: 'good' | 'warn' } {
  if (!online) return { text: 'Sin conexión en vivo: reconectando', tone: 'warn' }
  if (peers.some((p) => p.state === 'reconnecting')) return { text: 'Reconectando el audio y el video…', tone: 'warn' }
  if (peers.some((p) => p.state === 'connected')) return { text: 'Conexión estable', tone: 'good' }
  return { text: 'Esperando a la otra persona', tone: 'warn' }
}

function clockText(seconds: number) {
  const h = Math.floor(seconds / 3600)
  const m = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')
  const s = String(seconds % 60).padStart(2, '0')
  return h ? `${h}:${m}:${s}` : `${m}:${s}`
}

function Room({ item, company, media, room, seconds }: { item: InterviewItem; company: boolean; media: Media; room: Room; seconds: number }) {
  const [panel, setPanel] = useState(true)
  const [devices, setDevices] = useState(false)
  const [speaker, setSpeaker] = useState('')
  const main = room.peers.find((p) => p.present) ?? room.peers[0]
  const q = quality(room.peers, room.online)
  return <div className="fixed inset-0 z-50 flex flex-col bg-navy-950 text-white" role="region" aria-label="Videollamada en curso">
    <header className="flex flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3 sm:px-6">
      <div className="min-w-0"><h1 className="truncate text-base font-semibold">{item.interview.title}</h1>
        <p className="truncate text-[0.8125rem] text-azure-300">{company ? item.candidateEmail : item.companyName}, {item.vacancyTitle}</p></div>
      <span role="timer" aria-label="Duración de la llamada" className="ml-auto text-[0.9375rem] font-semibold tabular-nums">{clockText(seconds)}</span>
      <span className={cn('text-[0.8125rem]', q.tone === 'good' ? 'text-[#5eead4]' : 'text-[#fcd34d]')} aria-live="polite">{q.text}</span>
      <button type="button" onClick={() => setPanel(!panel)} aria-expanded={panel}
        className="hidden min-h-10 cursor-pointer rounded-[0.625rem] border border-navy-600 px-3.5 text-[0.8125rem] font-semibold md:inline-flex md:items-center">Detalles</button>
    </header>
    <div className="flex min-h-0 flex-1 gap-4 px-3 sm:px-6">
      <section aria-label={main ? `Video de ${main.label}` : 'Video de la otra persona'} className="relative grid min-w-0 flex-1 place-items-center overflow-hidden rounded-2xl bg-navy-800">
        {main?.stream && main.camOn ? <VideoTile stream={main.stream} label={`Video de ${main.label}`} sinkId={speaker} />
          : <span className="px-6 text-center text-sm text-azure-200">{!main?.present ? `Esperando a que ${main?.label ?? 'la otra persona'} entre a la sala` : main.camOn ? 'Conectando el video…' : `${main.label} tiene la cámara apagada`}</span>}
        {/* El audio se escucha aunque la otra persona apague la cámara. */}
        {main?.stream && !main.camOn && <VideoTile stream={main.stream} label={`Audio de ${main.label}`} className="hidden" sinkId={speaker} />}
        {main && <span className="absolute bottom-4 left-4 rounded-lg bg-navy-950/75 px-3 py-1.5 text-[0.8125rem] font-semibold">{main.label}{!main.micOn && ', micrófono silenciado'}</span>}
        {(room.error || media.lost) && <p role="alert" className="absolute top-4 left-1/2 max-w-[90%] -translate-x-1/2 rounded-[0.625rem] bg-mid-100 px-4 py-2.5 text-[0.8125rem] font-semibold text-mid-700">{media.lost ?? room.error}</p>}
        <div className="absolute right-4 bottom-4 grid aspect-video w-40 place-items-center overflow-hidden rounded-xl border-2 border-navy-600 bg-navy-700 sm:w-60" aria-label="Tu video">
          {media.stream && media.camOn && media.stream.getVideoTracks().length ? <VideoTile stream={media.stream} muted label="Tu video" className="-scale-x-100" />
            : <span className="text-xs text-azure-200">Tu cámara está apagada</span>}
          {!media.micOn && <span className="absolute top-2 left-2 rounded-md bg-low-700 px-2 py-0.5 text-xs font-semibold">Silenciado</span>}
        </div>
      </section>
      {panel && <aside aria-label="Detalles de la entrevista" className="hidden w-80 shrink-0 flex-col gap-5 overflow-y-auto rounded-2xl bg-navy-900 p-5 md:flex">
        <h2 className="text-[0.9375rem] font-semibold">En la sala</h2>
        <ul className="flex flex-col gap-2.5 text-sm">
          {room.peers.map((p) => <li key={p.userId} className="flex items-center gap-2.5"><span aria-hidden="true" className={cn('size-2 rounded-full', p.present ? 'bg-[#5eead4]' : 'bg-navy-600')} />
            <span className="min-w-0 truncate">{p.label}</span><span className="ml-auto text-xs text-azure-300">{p.present ? (p.state === 'connected' ? 'conectado' : 'conectando') : 'fuera'}</span></li>)}
          <li className="flex items-center gap-2.5"><span aria-hidden="true" className="size-2 rounded-full bg-[#5eead4]" />Tú<span className="ml-auto text-xs text-azure-300">dentro</span></li>
        </ul>
        <dl className="grid grid-cols-[5rem_minmax(0,1fr)] gap-2 text-[0.8125rem]"><dt className="text-azure-300">Horario</dt><dd>{longWhen(item.interview.startsAt, item.interview.durationMinutes)}</dd>
          <dt className="text-azure-300">Grabación</dt><dd>No se graba</dd></dl>
      </aside>}
    </div>
    {devices && <div className="mx-3 mt-3 rounded-2xl bg-white p-4 text-ink-900 sm:mx-6"><DeviceFields media={media} speaker={speaker} onSpeaker={setSpeaker} /></div>}
    <footer className="flex flex-wrap items-center justify-center gap-3 px-4 pt-4 pb-6">
      <MediaButton dark on={media.micOn} onClick={media.toggleMic} disabled={!media.stream?.getAudioTracks().length} onLabel="Silenciar" offLabel="Activar micrófono" Icon={media.micOn ? Mic : MicOff} />
      <MediaButton dark on={media.camOn} onClick={media.toggleCam} disabled={!media.stream?.getVideoTracks().length} onLabel="Apagar cámara" offLabel="Encender cámara" Icon={media.camOn ? Video : VideoOff} />
      <button type="button" aria-expanded={devices} onClick={() => setDevices(!devices)} className="inline-flex min-h-12 cursor-pointer items-center gap-2 rounded-full border border-navy-600 px-5 text-sm font-semibold"><Settings2 className="size-4" aria-hidden="true" />Dispositivos</button>
      <button type="button" onClick={() => void room.leave()} className="inline-flex min-h-12 cursor-pointer items-center gap-2 rounded-full bg-low-700 px-6 text-sm font-semibold"><PhoneOff className="size-4" aria-hidden="true" />Salir de la llamada</button>
      {company && <button type="button" onClick={() => void room.leave(true)} className="inline-flex min-h-12 cursor-pointer items-center rounded-full px-4 text-sm font-semibold text-azure-200 underline underline-offset-4">Terminar para todos</button>}
    </footer>
  </div>
}

function Ended({ item, company, seconds, onRejoin }: { item: InterviewItem; company: boolean; seconds: number; onRejoin: () => void }) {
  const i = item.interview
  const minutes = Math.max(1, Math.round(seconds / 60))
  return <div className="flex w-full justify-center px-4 py-14 sm:px-8">
    <div className="flex w-full max-w-2xl flex-col gap-7">
      <div className="flex flex-col gap-2"><h1 className="text-[1.75rem] font-bold tracking-tight text-navy-900">Saliste de la entrevista</h1>
        <p className="text-base leading-relaxed text-ink-700">{seconds > 0 ? `Estuviste ${minutes} ${minutes === 1 ? 'minuto' : 'minutos'} en la sala. ` : ''}La cámara y el micrófono ya están apagados.</p></div>
      <dl className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3 border-y border-ink-100 py-5 text-[0.9375rem]">
        <dt className="text-ink-500">Entrevista</dt><dd className="font-semibold">{i.title}, {company ? item.candidateEmail : item.companyName}</dd>
        <dt className="text-ink-500">Vacante</dt><dd>{item.vacancyTitle}</dd>
        <dt className="text-ink-500">Horario</dt><dd>{longWhen(i.startsAt, i.durationMinutes)}</dd>
        <dt className="text-ink-500">Estado</dt><dd>{company ? 'Recuerda registrar si la entrevista se realizó.' : 'La empresa registrará el resultado. Te avisaremos de cualquier cambio.'}</dd>
      </dl>
      <div className="flex flex-wrap gap-3">
        <Button asChild><AppLink href={company ? `/entrevistas?cita=${i.id}` : `/postulaciones?postulacion=${encodeURIComponent(i.applicationId)}`} className="text-white hover:text-white hover:no-underline">{company ? 'Volver a la entrevista' : 'Ver mi postulación'}</AppLink></Button>
        <Button asChild variant="secondary"><AppLink href={`/mensajes/${i.applicationId}`} className="text-navy-900 hover:no-underline">Ir a la conversación</AppLink></Button>
      </div>
      <p className="text-sm text-ink-700">¿Se cortó sin querer? <button type="button" onClick={onRejoin} className="cursor-pointer font-semibold text-azure-600 underline underline-offset-4">Volver a la sala</button>.</p>
    </div>
  </div>
}
