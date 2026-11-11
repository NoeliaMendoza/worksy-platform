import { CheckCircle2, LoaderCircle } from 'lucide-react'
import { useId, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { cvApi, formatSize, type Consents, type CvDocument } from '@/features/cv/api'
import { CvSheet } from '@/features/cv/cv-sheet'
import { formatDate } from '@/features/vacancy/labels'
import { errorMessage } from '@/lib/http'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { cn } from '@/lib/cn'
import { applicationsApi, type MyApplication } from './api'

type Data = { cvs: CvDocument[]; consents: Consents; application: MyApplication | null }

const uploadedFormat = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })

/**
 * Postular a una vacante publicada. Muestra lo que falta (un CV, el permiso de compartirlo)
 * en el mismo lugar donde se postula, sin mandar a la persona a buscarlo.
 */
export function ApplyPanel({ vacancyId, companyName }: { vacancyId: string; companyName: string }) {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const { state, retry, replace } = useResource<Data>(
    async () => {
      const [cvs, consents, mine] = await Promise.all([cvApi.list(token), cvApi.consents(token), applicationsApi.mine(token)])
      return { cvs, consents, application: mine.find((a) => a.vacancyId === vacancyId) ?? null }
    },
    `postular:${vacancyId}:${token}`,
    'No se pudo comprobar tu postulación.',
  )

  return (
    <section aria-labelledby="postular" className="rounded-panel bg-navy-900 p-6 text-white sm:p-8">
      <h2 id="postular" className="text-xl font-semibold">
        Postular a esta vacante
      </h2>
      {state.status === 'loading' && (
        <div className="mt-5 flex flex-col gap-3" aria-busy="true">
          <Skeleton className="h-5 w-2/3 bg-white/10" />
          <Skeleton className="h-10 w-40 bg-white/10" />
        </div>
      )}
      {state.status === 'error' && (
        <div className="mt-4 flex flex-col items-start gap-3">
          <p className="text-sm text-azure-200">{state.message}</p>
          <Button variant="inverse" size="sm" onClick={retry}>
            Reintentar
          </Button>
        </div>
      )}
      {state.status === 'ready' &&
        (state.data.application ? (
          <Applied
            token={token}
            application={state.data.application}
            onWithdrawn={() => replace({ ...state.data, application: null })}
          />
        ) : state.data.cvs.length === 0 ? (
          <div className="mt-4 flex flex-col items-start gap-4">
            <p className="max-w-md text-sm leading-relaxed text-azure-200">
              Necesitas un CV en PDF para postularte. Súbelo una vez y úsalo en todas tus postulaciones.
            </p>
            <Button variant="inverse" onClick={() => navigate('/mi-cv')}>
              Subir mi CV
            </Button>
          </div>
        ) : (
          <ApplyForm
            token={token}
            vacancyId={vacancyId}
            companyName={companyName}
            data={state.data}
            onApplied={(application, consents) => replace({ ...state.data, application, consents })}
          />
        ))}
    </section>
  )
}

function ApplyForm({
  token,
  vacancyId,
  companyName,
  data,
  onApplied,
}: {
  token: string
  vacancyId: string
  companyName: string
  data: Data
  onApplied: (application: MyApplication, consents: Consents) => void
}) {
  const groupId = useId()
  const [cvId, setCvId] = useState(data.cvs[0]!.id)
  const alreadyShared = data.consents.shareWithRecruiters
  const [share, setShare] = useState(alreadyShared)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit() {
    if (!share) {
      setError(`Para postular, permite que ${companyName} vea tu CV.`)
      return
    }
    setSending(true)
    setError(null)
    try {
      let consents = data.consents
      if (!alreadyShared) {
        consents = await cvApi.updateConsents(token, { shareWithRecruiters: true, githubLink: data.consents.githubLink })
      }
      await applicationsApi.apply(token, vacancyId, cvId)
      const mine = await applicationsApi.mine(token)
      const created = mine.find((a) => a.vacancyId === vacancyId)
      if (created) onApplied(created, consents)
    } catch (err) {
      setError(errorMessage(err, 'No se pudo enviar tu postulación. Vuelve a intentarlo.'))
      setSending(false)
    }
  }

  return (
    <div className="mt-5 flex flex-col gap-5">
      {data.cvs.length > 1 ? (
        <fieldset>
          <legend id={groupId} className="text-sm font-medium">
            Elige el CV con el que te postulas
          </legend>
          <div className="mt-3 flex flex-col gap-2">
            {data.cvs.map((cv) => (
              <label
                key={cv.id}
                className={cn(
                  'flex cursor-pointer items-center gap-3 rounded-2xl px-4 py-3 ring-1 transition-colors ring-inset',
                  cvId === cv.id ? 'bg-white/10 ring-azure-300' : 'ring-white/15 hover:bg-white/5',
                )}
              >
                <input
                  type="radio"
                  name={groupId}
                  value={cv.id}
                  checked={cvId === cv.id}
                  onChange={() => setCvId(cv.id)}
                  className="size-4 accent-azure-400"
                />
                <span className="text-sm">
                  Subido el {uploadedFormat.format(new Date(cv.uploadedAt))}
                  <span className="text-azure-200">, {formatSize(cv.sizeBytes)}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
      ) : (
        <div className="flex items-center gap-3">
          <CvSheet filled className="w-7 drop-shadow-none" />
          <p className="text-sm">
            Te postulas con tu CV subido el {uploadedFormat.format(new Date(data.cvs[0]!.uploadedAt))}.
          </p>
        </div>
      )}

      {!alreadyShared && (
        <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed">
          <input
            type="checkbox"
            checked={share}
            onChange={(e) => {
              setShare(e.target.checked)
              setError(null)
            }}
            className="mt-1 size-4 shrink-0 accent-azure-400"
          />
          <span>
            Permito que las empresas a las que me postule, empezando por {companyName}, vean mi CV.
            <span className="block text-azure-200">Puedes cambiarlo cuando quieras en Mi CV.</span>
          </span>
        </label>
      )}

      {error && <Notice tone="error">{error}</Notice>}

      <div>
        <Button variant="inverse" onClick={() => void submit()} disabled={sending}>
          {sending && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          Postular
        </Button>
      </div>
    </div>
  )
}

function Applied({
  token,
  application,
  onWithdrawn,
}: {
  token: string
  application: MyApplication
  onWithdrawn: () => void
}) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function cancel() {
    setConfirming(false)
    setError(null)
  }

  async function withdraw() {
    setBusy(true)
    setError(null)
    try {
      await applicationsApi.withdraw(token, application.id)
      onWithdrawn()
    } catch (err) {
      setError(errorMessage(err, 'No se pudo retirar la postulación. Vuelve a intentarlo.'))
      setBusy(false)
    }
  }

  return (
    <div className="mt-4 flex flex-col gap-4">
      <p role="status" className="flex items-start gap-3 text-base">
        <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-azure-300" aria-hidden="true" />
        <span>
          Te postulaste el {formatDate(application.appliedAt)}.
          <span className="mt-1 block text-sm text-azure-200">
            Tu CV se ordenará junto a los demás y una persona del equipo lo revisará.
          </span>
        </span>
      </p>
      {confirming ? (
        <div className="flex flex-col gap-3 rounded-2xl bg-white/10 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm">La empresa dejará de ver tu postulación.</p>
          <div className="flex gap-2">
            <Button variant="inverse" size="sm" onClick={() => void withdraw()} disabled={busy}>
              {busy && <LoaderCircle className="animate-spin" aria-hidden="true" />}
              Retirar postulación
            </Button>
            <Button size="sm" className="bg-transparent ring-1 ring-white/30 hover:bg-white/10" onClick={cancel} disabled={busy}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : (
        <div>
          <Button size="sm" className="bg-transparent ring-1 ring-white/30 hover:bg-white/10" onClick={() => setConfirming(true)}>
            Retirar postulación
          </Button>
        </div>
      )}
      {error && <Notice tone="error">{error}</Notice>}
    </div>
  )
}
