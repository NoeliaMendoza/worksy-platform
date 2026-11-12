import { EyeOff, FileText, Inbox, LoaderCircle } from 'lucide-react'
import { useState } from 'react'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { errorMessage } from '@/lib/http'
import { useResource } from '@/lib/use-resource'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { applicationsApi, type ReceivedApplication } from './api'
import { EyeOff, Inbox } from 'lucide-react'
import { StateMessage } from '@/components/state-message'
import { useResource } from '@/lib/use-resource'
import { LoadError, LoadingRows } from '@/features/vacancy/resource-states'
import { applicationsApi } from './api'

const dateTime = new Intl.DateTimeFormat('es', { dateStyle: 'long', timeStyle: 'short' })

/**
 * Postulaciones que recibió una vacante, en orden de llegada. Mientras la preselección automática
 * no esté activa, es la forma de ver quién se postuló. Sin permiso de la persona no se muestran sus datos.
 */
export function ReceivedApplications({ token, vacancyId }: { token: string; vacancyId: string }) {
  const { state, retry } = useResource(
    () => applicationsApi.forVacancy(token, vacancyId),
    `recibidas:${vacancyId}:${token}`,
    'No se pudieron cargar las postulaciones.',
  )

  return (
    <section aria-labelledby="postulaciones-recibidas" className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="postulaciones-recibidas" className="text-lg font-semibold text-ink-900">
          Postulaciones recibidas
        </h2>
        {state.status === 'ready' && state.data.length > 0 && (
          <p className="text-sm text-ink-500">{state.data.length === 1 ? '1 persona' : `${state.data.length} personas`}</p>
        )}
      </div>
      {state.status === 'loading' && <LoadingRows rows={2} />}
      {state.status === 'error' && (
        <LoadError title="No se pudieron cargar las postulaciones" message={state.message} onRetry={retry} />
      )}
      {state.status === 'ready' &&
        (state.data.length === 0 ? (
          <StateMessage
            icon={Inbox}
            title="Nadie se ha postulado todavía"
            description="Las postulaciones aparecerán aquí en orden de llegada."
            className="py-10"
          />
        ) : (
          <ol className="divide-y divide-ink-100 rounded-card bg-white shadow-soft">
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

function ApplicationRow({
  token,
  vacancyId,
  application: a,
  position,
}: {
  token: string
  vacancyId: string
  application: ReceivedApplication
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

  return (
    <li className="flex flex-col gap-3 px-5 py-4 sm:px-6">
      <div className="flex items-center gap-4">
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
