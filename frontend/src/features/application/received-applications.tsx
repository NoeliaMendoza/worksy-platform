import { EyeOff, FileText, Inbox, ListChecks, LoaderCircle } from 'lucide-react'
import { useRef, useState } from 'react'
import { StateMessage } from '@/components/state-message'
import { Badge, type BadgeProps } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { errorMessage } from '@/lib/http'
import { useResource } from '@/lib/use-resource'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import {
  applicationsApi,
  screeningApi,
  type ReceivedApplication,
  type ScreeningRequest,
  type ScreeningStatus,
} from './api'
import { applicationsApi, type ReceivedApplication } from './api'
import { EyeOff, Inbox } from 'lucide-react'
import { StateMessage } from '@/components/state-message'
import { useResource } from '@/lib/use-resource'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { applicationsApi } from './api'

const dateTime = new Intl.DateTimeFormat('es', { dateStyle: 'long', timeStyle: 'short' })

/** Estado de la preselección en palabras. Nunca dice "preseleccionado": eso lo decide una persona. */
const screeningLabel: Record<ScreeningStatus, { text: string; tone: NonNullable<BadgeProps['tone']> }> = {
  PENDING: { text: 'En cola de análisis', tone: 'neutral' },
  PROCESSING: { text: 'Analizando', tone: 'azure' },
  COMPLETED: { text: 'Análisis listo', tone: 'high' },
  NEEDS_REVIEW: { text: 'Análisis con dudas', tone: 'mid' },
  FAILED: { text: 'No se pudo analizar', tone: 'low' },
}

type Data = { applications: ReceivedApplication[]; screenings: ScreeningRequest[] }

/**
 * Postulaciones que recibió una vacante, en orden de llegada, con el estado de su preselección.
 * Sin permiso de la persona no se muestran sus datos ni se procesa su CV.
 */
export function ReceivedApplications({ token, vacancyId }: { token: string; vacancyId: string }) {
  const { state, retry, replace } = useResource<Data>(
    async () => {
      const [applications, screenings] = await Promise.all([
        applicationsApi.forVacancy(token, vacancyId),
        screeningApi.list(token, vacancyId),
      ])
      return { applications, screenings }
    },
    `recibidas:${vacancyId}:${token}`,
    'No se pudieron cargar las postulaciones.',
  )

  const byApplication = new Map(
    state.status === 'ready' ? state.data.screenings.map((s) => [s.applicationId, s] as const) : [],
  )

  return (
    <section aria-labelledby="postulaciones-recibidas" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-baseline gap-3">
          <h2 id="postulaciones-recibidas" className="text-lg font-semibold text-ink-900">
            Postulaciones recibidas
          </h2>
          {state.status === 'ready' && state.data.applications.length > 0 && (
            <p className="text-sm text-ink-500">
              {state.data.applications.length === 1 ? '1 persona' : `${state.data.applications.length} personas`}
            </p>
          )}
        </div>
        {state.status === 'ready' && state.data.applications.length > 0 && (
          <RequestScreening
            token={token}
            vacancyId={vacancyId}
            onRequested={(created) => replace({ ...state.data, screenings: [...state.data.screenings, ...created] })}
          />
        )}
      </div>
      {state.status === 'loading' && <LoadingRows rows={2} />}
      {state.status === 'error' && (
        <LoadError title="No se pudieron cargar las postulaciones" message={state.message} onRetry={retry} />
      )}
      {state.status === 'ready' &&
        (state.data.applications.length === 0 ? (
          <StateMessage
            icon={Inbox}
            title="Nadie se ha postulado todavía"
            description="Las postulaciones aparecerán aquí en orden de llegada."
            className="py-10"
          />
        ) : (
          <ol className="divide-y divide-ink-100 rounded-card bg-white shadow-soft">
            {state.data.applications.map((a, i) => (
              <ApplicationRow
                key={a.id}
                token={token}
                vacancyId={vacancyId}
                application={a}
                screening={byApplication.get(a.id)}
                position={i + 1}
              />
            {state.data.map((a, i) => (
              <ApplicationRow key={a.id} token={token} vacancyId={vacancyId} application={a} position={i + 1} />

              <li key={a.id} className="flex items-center gap-4 px-5 py-4 sm:px-6">
                <span className="w-6 text-right text-sm text-ink-500 tabular-nums">{i + 1}</span>
                <div className="min-w-0 flex-1">
                  {a.shared ? (
                    <p className="truncate text-sm font-semibold text-ink-900">{a.candidateEmail}</p>
                  ) : (
                    <p className="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-700">
                      <EyeOff className="size-4 text-ink-500" aria-hidden="true" />
                      Datos no compartidos
                    </p>
                  )}
                  <p className="text-sm text-ink-500">
                    {a.shared
                      ? `Se postuló el ${dateTime.format(new Date(a.appliedAt))}`
                      : 'La persona retiró el permiso de compartir su CV.'}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        ))}
    </section>
  )
}

/**
 * Pide la preselección de todas las postulaciones. La clave de idempotencia nace con el clic y se
 * reutiliza si hay que reintentar tras un error de red: así un reintento nunca duplica el trabajo.
 */
function RequestScreening({
  token,
  vacancyId,
  onRequested,
}: {
  token: string
  vacancyId: string
  onRequested: (created: ScreeningRequest[]) => void
}) {
  const pendingKey = useRef<string | null>(null)
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function request() {
    pendingKey.current ??= crypto.randomUUID()
    setSending(true)
    setResult(null)
    try {
      const batch = await screeningApi.request(token, vacancyId, pendingKey.current)
      pendingKey.current = null
      if (!batch.replayed) onRequested(batch.requests)
      setResult({ tone: 'success', text: summary(batch.requests.length, batch.skipped) })
    } catch (err) {
      setResult({ tone: 'error', text: errorMessage(err, 'No se pudo pedir la preselección. Vuelve a intentarlo.') })
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="flex flex-col items-end gap-2">
      <Button onClick={() => void request()} disabled={sending}>
        {sending ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <ListChecks aria-hidden="true" />}
        Preseleccionar CV
      </Button>
      {result && (
        <Notice tone={result.tone} className="max-w-md">
          {result.text}
        </Notice>
      )}
    </div>
  )
}

function summary(created: number, skipped: number): string {
  const head =
    created === 0
      ? 'No hay CV nuevos para analizar.'
      : created === 1
        ? 'Se pidió el análisis de 1 CV. Aparecerá en el ranking cuando termine.'
        : `Se pidió el análisis de ${created} CV. Aparecerán en el ranking cuando terminen.`
  if (skipped === 0) return head
  const tail =
    skipped === 1
      ? '1 postulación no se incluyó: ya estaba pedida o la persona no comparte su CV.'
      : `${skipped} postulaciones no se incluyeron: ya estaban pedidas o la persona no comparte su CV.`
  return `${head} ${tail}`
}

function ApplicationRow({
  token,
  vacancyId,
  application: a,
  screening,
  position,
}: {
  token: string
  vacancyId: string
  application: ReceivedApplication
  screening: ScreeningRequest | undefined
  position: number
}) {
  const [opening, setOpening] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /**
   * La pestaña se abre en el mismo clic (si se abriera después de esperar a la API, el navegador
   * la bloquearía como ventana emergente) y recibe el enlace temporal cuando llega.
   */
  async function openCv() {
    setOpening(true)
    setError(null)
    const tab = window.open('', '_blank')
    try {
      const { url } = await applicationsApi.cvLink(token, vacancyId, a.id)
      if (tab) {
        tab.opener = null
        tab.location.href = url
      } else {
        window.location.assign(url)
      }
    } catch (err) {
      tab?.close()
      setError(errorMessage(err, 'No se pudo abrir el CV. Vuelve a intentarlo.'))
    } finally {
      setOpening(false)
    }
  }

  const label = screening ? screeningLabel[screening.status] : null
  return (
    <li className="flex flex-col gap-3 px-5 py-4 sm:px-6">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <span className="w-6 text-right text-sm text-ink-500 tabular-nums">{position}</span>
        <div className="min-w-0 flex-1">
          {a.shared ? (
            <p className="truncate text-sm font-semibold text-ink-900">{a.candidateEmail}</p>
          ) : (
            <p className="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-700">
              <EyeOff className="size-4 text-ink-500" aria-hidden="true" />
              Datos no compartidos
            </p>
          )}
          <p className="text-sm text-ink-500">
            {a.shared
              ? `Se postuló el ${dateTime.format(new Date(a.appliedAt))}`
              : 'La persona retiró el permiso de compartir su CV.'}
          </p>
        </div>
        {label && <Badge tone={label.tone}>{label.text}</Badge>}
        {a.shared && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => void openCv()}
            disabled={opening}
            aria-label={`Ver el CV de ${a.candidateEmail ?? 'esta postulación'} (se abre en otra pestaña)`}
          >
            {opening ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <FileText aria-hidden="true" />}
            Ver CV
          </Button>
        )}
      </div>
      {error && <Notice tone="error">{error}</Notice>}
    </li>
  )
}
