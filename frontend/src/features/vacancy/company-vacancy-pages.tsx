import { ArrowLeft, ListOrdered, LoaderCircle, Lock } from 'lucide-react'
import { useState } from 'react'
import { AppLink } from '@/components/app-link'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { useAuth } from '@/features/auth/auth-context'
import { ReceivedApplications } from '@/features/application/received-applications'
import { errorMessage } from '@/lib/http'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { companyVacancies, type CompanyVacancy } from './api'
import { formatDate, kindLabel, modalityLabel, statusLabel, statusTone } from './labels'
import { LoadError, LoadingRows } from './resource-states'
import { VacancyEditor } from './vacancy-editor'

function useToken() {
  const { state } = useAuth()
  return state.status === 'authenticated' ? state.accessToken : ''
}

function BackToList() {
  return (
    <AppLink href="/vacantes" className="inline-flex items-center gap-1.5 text-sm">
      <ArrowLeft className="size-4" aria-hidden="true" />
      Tus vacantes
    </AppLink>
  )
}

const pageClass = 'mx-auto flex max-w-4xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10'

/** Crear una vacante nueva (queda en borrador o se publica directamente). */
export function NewVacancyPage() {
  const token = useToken()
  return (
    <div className={pageClass}>
      <header>
        <BackToList />
        <h1 className="mt-4 text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">
          Nueva <span className="font-semibold">vacante</span>
        </h1>
      </header>
      <VacancyEditor token={token} onSaved={(v) => navigate(`/vacantes/${v.id}`)} />
    </div>
  )
}

/** Detalle de una vacante de la empresa: editor si es borrador; resumen, ranking y cierre si no. */
export function CompanyVacancyPage({ id }: { id: string }) {
  const token = useToken()
  const { state, retry, replace } = useResource(() => companyVacancies.get(token, id), `vacante:${id}:${token}`, 'No se pudo cargar la vacante.')
  const [saved, setSaved] = useState(false)

  if (state.status === 'loading') {
    return (
      <div className={pageClass}>
        <BackToList />
        <LoadingRows />
      </div>
    )
  }
  if (state.status === 'error') {
    return (
      <div className={pageClass}>
        <BackToList />
        <LoadError title="No se pudo cargar la vacante" message={state.message} onRetry={retry} />
      </div>
    )
  }
  const v = state.data
  return (
    <div className={pageClass}>
      <header>
        <BackToList />
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <h1 className="text-4xl leading-[1.1] font-light tracking-tight text-navy-900 sm:text-5xl">{v.title}</h1>
          <Badge tone={statusTone[v.status]}>{statusLabel[v.status]}</Badge>
        </div>
      </header>

      {v.status === 'DRAFT' ? (
        <>
          {saved && (
            <Notice tone="success" autoFocus>
              Borrador guardado. Cuando esté listo, publícalo para recibir CV.
            </Notice>
          )}
          <VacancyEditor
            key={v.updatedAt}
            token={token}
            initial={v}
            onSaved={(next) => {
              setSaved(next.status === 'DRAFT')
              replace(next)
            }}
          />
        </>
      ) : (
        <PublishedVacancy vacancy={v} token={token} onChanged={replace} />
      )}
    </div>
  )
}

function PublishedVacancy({
  vacancy: v,
  token,
  onChanged,
}: {
  vacancy: CompanyVacancy
  token: string
  onChanged: (v: CompanyVacancy) => void
}) {
  const [confirming, setConfirming] = useState(false)
  const [closing, setClosing] = useState(false)
  const [error, setError] = useState('')

  async function close() {
    setClosing(true)
    setError('')
    try {
      onChanged(await companyVacancies.close(token, v.id))
      setConfirming(false)
    } catch (err) {
      setError(errorMessage(err, 'No se pudo cerrar la vacante.'))
    } finally {
      setClosing(false)
    }
  }

  return (
    <>
      <div className="flex flex-wrap gap-3">
        <Button onClick={() => navigate(`/vacantes/${v.id}/ranking`)}>
          <ListOrdered aria-hidden="true" />
          Ver ranking de CV
        </Button>
        {v.status === 'PUBLISHED' && !confirming && (
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            <Lock aria-hidden="true" />
            Cerrar vacante
          </Button>
        )}
      </div>

      {confirming && (
        <div role="group" aria-labelledby="confirmar-cierre" className="rounded-card bg-mid-100 p-5">
          <p id="confirmar-cierre" className="font-semibold text-ink-900">
            ¿Cerrar esta vacante?
          </p>
          <p className="mt-1 text-sm text-ink-700">
            Dejará de verse en la lista pública y no recibirá más CV. No se puede volver a abrir.
          </p>
          {error && (
            <Notice tone="error" autoFocus className="mt-3">
              {error}
            </Notice>
          )}
          <div className="mt-4 flex flex-wrap gap-2">
            <Button onClick={close} disabled={closing}>
              {closing && <LoaderCircle className="animate-spin" aria-hidden="true" />}
              Sí, cerrar vacante
            </Button>
            <Button variant="secondary" onClick={() => setConfirming(false)} disabled={closing}>
              Cancelar
            </Button>
          </div>
        </div>
      )}

      <ReceivedApplications token={token} vacancyId={v.id} />
      <VacancySummary vacancy={v} />
    </>
  )
}

/** Resumen de solo lectura: datos, descripción y criterios. */
function VacancySummary({ vacancy: v }: { vacancy: CompanyVacancy }) {
  return (
    <div className="flex flex-col gap-6">
      <dl className="grid gap-4 rounded-card bg-white p-5 shadow-soft sm:grid-cols-3 sm:p-6">
        <div>
          <dt className="text-sm text-ink-500">Ubicación</dt>
          <dd className="font-semibold text-ink-900">
            {v.location}, {modalityLabel[v.modality].toLowerCase()}
          </dd>
        </div>
        <div>
          <dt className="text-sm text-ink-500">Publicada</dt>
          <dd className="font-semibold text-ink-900">{formatDate(v.publishedAt)}</dd>
        </div>
        {v.closedAt && (
          <div>
            <dt className="text-sm text-ink-500">Cerrada</dt>
            <dd className="font-semibold text-ink-900">{formatDate(v.closedAt)}</dd>
          </div>
        )}
      </dl>
      <section aria-labelledby="descripcion" className="rounded-card bg-white p-5 shadow-soft sm:p-6">
        <h2 id="descripcion" className="text-lg font-semibold text-ink-900">
          Descripción
        </h2>
        <p className="mt-2 text-base leading-relaxed whitespace-pre-line text-ink-700">{v.description}</p>
      </section>
      <CriteriaList criteria={v.criteria} />
    </div>
  )
}

export function CriteriaList({ criteria, title = 'Qué se evalúa en cada CV' }: { criteria: CompanyVacancy['criteria']; title?: string }) {
  return (
    <section aria-labelledby="criterios-lista" className="rounded-card bg-white p-5 shadow-soft sm:p-6">
      <h2 id="criterios-lista" className="text-lg font-semibold text-ink-900">
        {title}
      </h2>
      <ol className="mt-4 flex flex-col gap-4">
        {criteria.map((c) => (
          <li key={c.key}>
            <p className="flex flex-wrap items-center gap-2 font-semibold text-ink-900">
              {c.label}
              <Badge tone="azure">{kindLabel[c.kind]}</Badge>
            </p>
            <p className="mt-1 text-ink-700">{c.question}</p>
            {c.kind === 'CHOICE' && (
              <p className="mt-1 text-sm text-ink-500">Opciones: {c.options.join(' / ')}</p>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}

export { RealRankingPage as VacancyRankingPage } from '@/features/ranking/real-ranking-page'
