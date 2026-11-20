import { ArrowLeft, Info, LoaderCircle, MessageSquare, MessagesSquare, SendHorizontal, WifiOff } from 'lucide-react'
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { AppLink } from '@/components/app-link'
import { PageHeader } from '@/components/page-header'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { Textarea } from '@/components/ui/textarea'
import { ApiError, type Role } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { cn } from '@/lib/cn'
import { errorMessage } from '@/lib/http'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { MESSAGE_MAX, messagesApi, type Conversation, type Message, type MessagePage } from './api'

const time = new Intl.DateTimeFormat('es', { timeStyle: 'short' })
const dayAndTime = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

function when(iso: string) {
  const date = new Date(iso)
  return date.toDateString() === new Date().toDateString() ? time.format(date) : dayAndTime.format(date)
}

/** Con quién es la conversación, visto desde cada lado. Sin permiso, la empresa no ve el correo. */
function counterpart(c: Conversation | undefined, candidate: boolean) {
  if (!c) return candidate ? 'Conversación' : 'Conversación nueva'
  return candidate ? c.companyName : (c.candidateEmail ?? 'Persona candidata')
}

/**
 * Mensajes entre la empresa y la persona candidata, uno por postulación.
 * Escritorio: bandeja a la izquierda y conversación a la derecha. Móvil: una cosa a la vez.
 */
export function MessagesPage({ applicationId }: { applicationId?: string }) {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const role: Role = auth.status === 'authenticated' ? auth.user.role : 'CANDIDATE'
  const candidate = role === 'CANDIDATE'
  const realtime = useRealtime()
  const inbox = useResource(() => messagesApi.inbox(token), `bandeja:${token}`, 'No se pudieron cargar tus mensajes.')
  const { refresh } = inbox
  useRealtimeNotices(refresh)

  const conversations = inbox.state.status === 'ready' ? inbox.state.data : []
  const open = conversations.find((c) => c.applicationId === applicationId)
  const emptyInbox = inbox.state.status === 'ready' && conversations.length === 0 && !applicationId

  return (
    <div className="app-page">
      <div className={cn(applicationId && 'hidden lg:block')}>
        <PageHeader title="Tus mensajes" description={candidate
            ? 'Conversaciones con las empresas sobre tus postulaciones. Puedes responder cuando una empresa te escriba.'
            : 'Conversaciones con las personas que se postularon a tus vacantes.'} />
      </div>

      {realtime?.status === 'offline' && (
        <p role="status" className="flex items-center gap-2 text-sm text-ink-500">
          <WifiOff className="size-4" aria-hidden="true" />
          Sin conexión en vivo. Los mensajes nuevos aparecerán cuando vuelva la conexión.
        </p>
      )}

      <div className="lg:grid lg:grid-cols-[minmax(16rem,0.8fr)_minmax(0,2fr)] lg:items-start lg:gap-4">
        <section aria-label="Conversaciones" className={cn(applicationId && 'hidden lg:block', emptyInbox && 'lg:col-span-2')}>
          {inbox.state.status === 'loading' && <LoadingRows rows={3} />}
          {inbox.state.status === 'error' && (
            <LoadError title="No se pudieron cargar tus mensajes" message={inbox.state.message} onRetry={inbox.retry} />
          )}
          {inbox.state.status === 'ready' &&
            (conversations.length === 0 ? (
              <StateMessage
                icon={MessagesSquare}
                title={candidate ? 'Aún no tienes mensajes' : 'Todavía no hay conversaciones'}
                description={
                  candidate
                    ? 'Cuando una empresa te escriba por una de tus postulaciones, la conversación aparecerá aquí.'
                    : 'Escribe a una persona desde las postulaciones recibidas de una vacante.'
                }
                action={!candidate && <Button onClick={() => navigate('/vacantes')}>Ir a vacantes</Button>}
                className="py-10"
              />
            ) : (
              <ul className="divide-y divide-ink-100 overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft">
                {conversations.map((c) => (
                  <li key={c.applicationId}>
                    <InboxRow conversation={c} candidate={candidate} current={c.applicationId === applicationId} />
                  </li>
                ))}
              </ul>
            ))}
        </section>

        {!emptyInbox && <section
          aria-label="Conversación"
          className={cn(!applicationId && 'hidden lg:flex', 'min-w-0 flex-col')}
        >
          {applicationId ? (
            <Thread
              key={applicationId}
              token={token}
              applicationId={applicationId}
              candidate={candidate}
              conversation={open}
              onRead={refresh}
            />
          ) : (
            <StateMessage
              icon={MessageSquare}
              title="Elige una conversación"
              description="Los mensajes aparecerán aquí."
              className="w-full py-8"
            />
          )}
        </section>}
      </div>
    </div>
  )
}

function InboxRow({ conversation: c, candidate, current }: { conversation: Conversation; candidate: boolean; current: boolean }) {
  const mineLast = candidate ? c.lastFromCandidate : !c.lastFromCandidate
  return (
    <AppLink
      href={`/mensajes/${c.applicationId}`}
      aria-current={current ? 'page' : undefined}
      className={cn(
        'flex flex-col gap-1 rounded-none px-4 py-3 font-normal text-ink-900 no-underline hover:bg-azure-50 hover:text-ink-900 hover:no-underline',
        current && 'bg-azure-100 hover:bg-azure-100',
      )}
    >
      <span className="flex items-baseline gap-3">
        <span className={cn('min-w-0 flex-1 truncate text-sm', c.unread > 0 ? 'font-bold' : 'font-semibold')}>
          {counterpart(c, candidate)}
        </span>
        <time dateTime={c.lastSentAt} className="shrink-0 text-xs text-ink-500">
          {when(c.lastSentAt)}
        </time>
      </span>
      <span className="truncate text-xs text-ink-500">{c.vacancyTitle}</span>
      <span className="flex items-center gap-3">
        <span className={cn('min-w-0 flex-1 truncate text-sm', c.unread > 0 ? 'text-ink-900' : 'text-ink-700')}>
          {mineLast && <span className="text-ink-500">{candidate ? 'Tú: ' : 'Tu equipo: '}</span>}
          {c.lastMessage}
        </span>
        {c.unread > 0 && (
          <span className="grid h-5 min-w-5 shrink-0 place-items-center rounded-full bg-azure-600 px-1.5 text-xs font-semibold text-white">
            {c.unread}
            <span className="sr-only"> sin leer</span>
          </span>
        )}
      </span>
    </AppLink>
  )
}

type ThreadState =
  | { status: 'loading' }
  | { status: 'missing' }
  | { status: 'error'; message: string }
  | { status: 'ready'; page: MessagePage }

/** Une mensajes por id y los deja en orden cronológico (los avisos pueden traer repetidos). */
function merge(current: Message[], incoming: Message[]) {
  const byId = new Map(current.map((m) => [m.id, m]))
  incoming.forEach((m) => byId.set(m.id, m))
  return [...byId.values()].sort((a, b) => a.sentAt.localeCompare(b.sentAt) || a.id.localeCompare(b.id))
}

function Thread({
  token,
  applicationId,
  candidate,
  conversation,
  onRead,
}: {
  token: string
  applicationId: string
  candidate: boolean
  conversation: Conversation | undefined
  onRead: () => void
}) {
  const [state, setState] = useState<ThreadState>({ status: 'loading' })
  const [olderLoading, setOlderLoading] = useState(false)
  const realtime = useRealtime()
  const emit = realtime?.emit
  const log = useRef<HTMLOListElement>(null)
  const stickToBottom = useRef(true)

  const markRead = useCallback(() => {
    messagesApi.markRead(token, applicationId).then(
      () => {
        emit?.({ type: 'conversation.read', applicationId })
        onRead()
      },
      () => undefined,
    )
  }, [token, applicationId, emit, onRead])

  /** Trae la página más reciente y la une con lo que ya se ve (incluidos mensajes anteriores ya cargados). */
  const loadLatest = useCallback(
    (first: boolean) =>
      messagesApi.page(token, applicationId).then(
        (latest) => {
          setState((s) =>
            s.status === 'ready'
              ? { status: 'ready', page: { ...latest, hasMore: s.page.hasMore, messages: merge(s.page.messages, latest.messages) } }
              : { status: 'ready', page: latest },
          )
          markRead()
        },
        (err: unknown) => {
          if (err instanceof ApiError && err.status === 404) setState({ status: 'missing' })
          else if (first) setState({ status: 'error', message: errorMessage(err, 'No se pudo cargar la conversación.') })
        },
      ),
    [token, applicationId, markRead],
  )

  useEffect(() => {
    void loadLatest(true)
    // Solo al abrir la conversación; los avisos en vivo piden lo nuevo después.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId])

  useRealtimeNotices((notice) => {
    if (notice.type === 'reconnected' || (notice.type === 'message.sent' && notice.applicationId === applicationId)) {
      void loadLatest(false)
    }
  })

  // Baja hasta el último mensaje si la persona ya estaba abajo (no la mueve si está leyendo más arriba).
  const count = state.status === 'ready' ? state.page.messages.length : 0
  useLayoutEffect(() => {
    const el = log.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [count])

  async function loadOlder() {
    if (state.status !== 'ready' || state.page.messages.length === 0) return
    setOlderLoading(true)
    stickToBottom.current = false
    try {
      const older = await messagesApi.page(token, applicationId, state.page.messages[0]!.sentAt)
      setState((s) =>
        s.status === 'ready'
          ? { status: 'ready', page: { ...s.page, hasMore: older.hasMore, messages: merge(s.page.messages, older.messages) } }
          : s,
      )
    } catch {
      // Se puede volver a intentar con el mismo botón.
    } finally {
      setOlderLoading(false)
    }
  }

  const title = counterpart(conversation, candidate)
  return (
    <div className="flex min-h-[28rem] flex-col overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft lg:h-[calc(100dvh-12rem)]">
      <header className="flex items-center gap-3 border-b border-ink-100 px-4 py-3 sm:px-5">
        <AppLink
          href="/mensajes"
          className="grid size-10 shrink-0 place-items-center rounded-full text-navy-700 hover:bg-azure-50 lg:hidden"
        >
          <ArrowLeft className="size-5" aria-hidden="true" />
          <span className="sr-only">Volver a mensajes</span>
        </AppLink>
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-ink-900">{title}</h2>
          {conversation && <p className="truncate text-sm text-ink-500">{conversation.vacancyTitle}</p>}
        </div>
      </header>

      {state.status === 'loading' && (
        <div className="p-5">
          <LoadingRows rows={2} />
        </div>
      )}
      {state.status === 'error' && (
        <div className="p-5">
          <LoadError title="No se pudo cargar la conversación" message={state.message} onRetry={() => void loadLatest(true)} />
        </div>
      )}
      {state.status === 'missing' && (
        <StateMessage
          icon={MessagesSquare}
          title="Esta conversación no existe"
          description="Puede que la postulación se haya retirado."
          action={<Button onClick={() => navigate('/mensajes')}>Ver mis mensajes</Button>}
          className="flex-1 shadow-none"
        />
      )}
      {state.status === 'ready' && (
        <>
          <ol
            ref={log}
            role="log"
            aria-label={`Mensajes con ${title}`}
            onScroll={(e) => {
              const el = e.currentTarget
              stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 48
            }}
            className="flex flex-1 flex-col gap-3 overflow-y-auto bg-azure-50 px-4 py-5 sm:px-5"
          >
            {state.page.hasMore && (
              <li className="self-center">
                <Button variant="secondary" size="sm" onClick={() => void loadOlder()} disabled={olderLoading}>
                  {olderLoading && <LoaderCircle className="animate-spin" aria-hidden="true" />}
                  Ver mensajes anteriores
                </Button>
              </li>
            )}
            {state.page.messages.length === 0 && (
              <li className="m-auto max-w-sm text-center text-sm text-ink-500">
                {candidate
                  ? 'Todavía no hay mensajes en esta postulación.'
                  : 'Todavía no hay mensajes. Escribe el primero: la persona podrá responderte aquí.'}
              </li>
            )}
            {state.page.messages.map((m) => (
              <Bubble key={m.id} message={m} candidate={candidate} counterpartName={title} />
            ))}
          </ol>
          <Composer
            token={token}
            applicationId={applicationId}
            canWrite={state.page.canWrite}
            candidate={candidate}
            onSent={(sent) => {
              stickToBottom.current = true
              setState((s) => (s.status === 'ready' ? { status: 'ready', page: { ...s.page, messages: merge(s.page.messages, [sent]) } } : s))
              onRead()
            }}
          />
        </>
      )}
    </div>
  )
}

function Bubble({ message: m, candidate, counterpartName }: { message: Message; candidate: boolean; counterpartName: string }) {
  // Mi lado de la conversación va a la derecha. En la empresa, los mensajes del equipo también.
  const ownSide = candidate ? m.fromCandidate : !m.fromCandidate
  const author = m.mine ? 'Tú' : ownSide ? 'Tu equipo' : counterpartName
  return (
    <li className={cn('flex max-w-[85%] flex-col gap-1 sm:max-w-[70%]', ownSide ? 'items-end self-end' : 'items-start self-start')}>
      {!m.mine && ownSide && <span className="px-1 text-xs font-medium text-ink-500">Tu equipo</span>}
      <p
        className={cn(
          'rounded-3xl px-4 py-2.5 text-sm leading-relaxed break-words whitespace-pre-wrap',
          ownSide ? 'rounded-br-lg bg-navy-900 text-white' : 'rounded-bl-lg bg-white text-ink-900 ring-1 ring-ink-100',
        )}
      >
        <span className="sr-only">{author}: </span>
        {m.body}
      </p>
      <time dateTime={m.sentAt} className="px-1 text-xs text-ink-500">
        {when(m.sentAt)}
      </time>
    </li>
  )
}

function Composer({
  token,
  applicationId,
  canWrite,
  candidate,
  onSent,
}: {
  token: string
  applicationId: string
  canWrite: boolean
  candidate: boolean
  onSent: (message: Message) => void
}) {
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const id = useId()
  const field = useRef<HTMLTextAreaElement>(null)
  const length = [...text].length
  const remaining = MESSAGE_MAX - length

  if (!canWrite) {
    return (
      <p className="flex gap-3 border-t border-ink-100 px-5 py-4 text-sm leading-relaxed text-ink-700">
        <Info className="mt-0.5 size-4 shrink-0 text-navy-700" aria-hidden="true" />
        {candidate
          ? 'Podrás responder cuando la empresa te escriba por esta postulación.'
          : 'La persona retiró el permiso de compartir sus datos. Puedes leer la conversación, pero no escribirle.'}
      </p>
    )
  }

  async function send(e?: FormEvent) {
    e?.preventDefault()
    if (sending || !text.trim() || remaining < 0) return
    setSending(true)
    setError('')
    try {
      onSent(await messagesApi.send(token, applicationId, text))
      setText('')
    } catch (err) {
      setError(errorMessage(err, 'No se pudo enviar el mensaje. Vuelve a intentarlo.'))
    } finally {
      setSending(false)
      field.current?.focus()
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter envía; Mayús + Enter salta de línea. No interfiere al escribir con un editor de acentos (IME).
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void send()
    }
  }

  return (
    <form onSubmit={(e) => void send(e)} className="flex flex-col gap-2 border-t border-ink-100 p-3 sm:p-4">
      {error && <Notice tone="error">{error}</Notice>}
      <div className="flex items-end gap-2">
        <label htmlFor={id} className="sr-only">
          Escribe un mensaje
        </label>
        <Textarea
          id={id}
          ref={field}
          rows={2}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Escribe un mensaje…"
          aria-describedby={`${id}-ayuda`}
          aria-invalid={remaining < 0 ? true : undefined}
          className="max-h-40 min-h-12 resize-none"
        />
        <Button type="submit" size="icon" disabled={sending || !text.trim() || remaining < 0} className="size-12">
          {sending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <SendHorizontal aria-hidden="true" />}
          <span className="sr-only">{sending ? 'Enviando…' : 'Enviar'}</span>
        </Button>
      </div>
      <p id={`${id}-ayuda`} className="flex flex-wrap justify-between gap-x-4 px-1 text-xs text-ink-500">
        <span>Enter para enviar · Mayús + Enter para otra línea</span>
        {remaining <= 200 && (
          <span aria-live="polite" className={cn(remaining < 0 && 'font-semibold text-low-700')}>
            {remaining < 0 ? `Sobran ${-remaining} caracteres` : `Quedan ${remaining} caracteres`}
          </span>
        )}
      </p>
    </form>
  )
}
