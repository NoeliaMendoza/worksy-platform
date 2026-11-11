import { LoaderCircle } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState, type DragEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { LoadError } from '@/features/vacancy/resource-states'
import { errorMessage } from '@/lib/http'
import { cn } from '@/lib/cn'
import { cvApi, formatSize, MAX_CVS, precheck, type Consents, type CvDocument } from './api'
import { CvSheet } from './cv-sheet'

type Load =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; cvs: CvDocument[]; consents: Consents }

const uploadedFormat = new Intl.DateTimeFormat('es', { dateStyle: 'long', timeStyle: 'short' })

/** "Tu CV": subir, ver y eliminar los CV de la persona candidata, y decidir quién puede verlos. */
export function CvPage() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const [load, setLoad] = useState<Load>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [announcement, setAnnouncement] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([cvApi.list(token), cvApi.consents(token)]).then(
      ([cvs, consents]) => active && setLoad({ status: 'ready', cvs, consents }),
      (err: unknown) => active && setLoad({ status: 'error', message: errorMessage(err, 'No se pudo cargar tu CV.') }),
    )
    return () => {
      active = false
    }
  }, [token, attempt])

  const retry = useCallback(() => {
    setLoad({ status: 'loading' })
    setAttempt((n) => n + 1)
  }, [])

  const update = (change: (ready: Extract<Load, { status: 'ready' }>) => Partial<Extract<Load, { status: 'ready' }>>) =>
    setLoad((current) => (current.status === 'ready' ? { ...current, ...change(current) } : current))

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-10 px-4 py-6 sm:px-8 sm:py-10">
      <header>
        <h1 className="text-4xl leading-[1.1] font-semibold tracking-tight text-navy-900 sm:text-5xl">Tu CV</h1>
        <p className="mt-3 max-w-xl text-base leading-relaxed text-ink-700">
          Sube tu CV en PDF para postularte a las vacantes. Las empresas solo lo ven si tú lo permites.
        </p>
      </header>

      <p role="status" aria-live="polite" className="sr-only">
        {load.status === 'loading' ? 'Cargando tu CV.' : announcement}
      </p>

      {load.status === 'loading' && (
        <div className="flex flex-col gap-4" aria-busy="true">
          <Skeleton className="h-48 rounded-panel" />
          <Skeleton className="h-20 rounded-card" />
        </div>
      )}

      {load.status === 'error' && <LoadError title="No se pudo cargar tu CV" message={load.message} onRetry={retry} />}

      {load.status === 'ready' && (
        <>
          <UploadZone
            // Si la lista cambia (se subió o se eliminó un CV), un aviso anterior de subida ya no aplica.
            key={load.cvs.length}
            token={token}
            full={load.cvs.length >= MAX_CVS}
            hasCv={load.cvs.length > 0}
            onUploaded={(cv) => {
              update((r) => ({ cvs: [cv, ...r.cvs] }))
              setAnnouncement('CV subido.')
            }}
          />
          {load.cvs.length > 0 && (
            <CvList
              token={token}
              cvs={load.cvs}
              onRemoved={(id) => {
                update((r) => ({ cvs: r.cvs.filter((c) => c.id !== id) }))
                setAnnouncement('CV eliminado.')
              }}
            />
          )}
          <PrivacySection
            token={token}
            consents={load.consents}
            onSaved={(consents) => {
              update(() => ({ consents }))
              setAnnouncement(consents.shareWithRecruiters ? 'Las empresas a las que te postules podrán ver tu CV.' : 'Tu CV ya no se comparte.')
            }}
          />
        </>
      )}
    </div>
  )
}

// ---------------- Subida ----------------

function UploadZone({
  token,
  full,
  hasCv,
  onUploaded,
}: {
  token: string
  full: boolean
  hasCv: boolean
  onUploaded: (cv: CvDocument) => void
}) {
  const inputId = useId()
  const hintId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)

  async function send(file: File | undefined) {
    if (!file || uploading) return
    setError(null)
    const problem = precheck(file)
    if (problem) {
      setError(problem)
      return
    }
    setUploading(file.name)
    try {
      onUploaded(await cvApi.upload(token, file))
    } catch (err) {
      setError(errorMessage(err, 'No se pudo subir el CV. Vuelve a intentarlo.'))
    } finally {
      setUploading(null)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    if (!full) void send(e.dataTransfer.files[0])
  }

  if (full) {
    return (
      <section aria-labelledby={`${inputId}-full`} className="flex items-center gap-6 rounded-panel bg-white p-6 shadow-soft sm:p-8">
        <CvSheet filled className="w-16" />
        <div>
          <h2 id={`${inputId}-full`} className="text-lg font-semibold text-ink-900">
            Tienes {MAX_CVS} CV, el máximo
          </h2>
          <p className="mt-1 text-sm leading-relaxed text-ink-700">Elimina uno de la lista para subir otro.</p>
        </div>
      </section>
    )
  }

  return (
    <section aria-label="Subir CV" className="flex flex-col gap-3">
      <div
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'relative flex flex-col items-center gap-6 rounded-panel bg-white p-8 text-center shadow-soft ring-2 ring-transparent transition-[box-shadow,background-color] sm:flex-row sm:p-10 sm:text-left',
          dragging && 'bg-azure-50 ring-azure-500',
        )}
      >
        <CvSheet scanning={uploading !== null} filled={uploading !== null} />
        <div className="flex min-w-0 flex-col items-center gap-4 sm:items-start">
          {uploading ? (
            <div>
              <p className="text-lg font-semibold text-ink-900">Subiendo tu CV…</p>
              <p className="mt-1 truncate text-sm text-ink-500" title={uploading}>
                {uploading}
              </p>
            </div>
          ) : (
            <div>
              <p className="text-lg font-semibold text-ink-900">{hasCv ? 'Sube una versión nueva' : 'Arrastra tu CV aquí'}</p>
              <p id={hintId} className="mt-1 text-sm text-ink-500">
                PDF de hasta 5 MB.
              </p>
            </div>
          )}
          <input
            ref={inputRef}
            id={inputId}
            type="file"
            accept="application/pdf,.pdf"
            className="sr-only"
            aria-describedby={hintId}
            disabled={uploading !== null}
            onChange={(e) => void send(e.target.files?.[0])}
          />
          <label
            htmlFor={inputId}
            className={cn(
              'inline-flex h-10 cursor-pointer items-center gap-2 rounded-full bg-navy-900 px-5 text-sm font-semibold text-white transition-colors hover:bg-navy-700',
              // El input real está oculto: su foco de teclado se dibuja en esta etiqueta.
              '[input:focus-visible+&]:outline-2 [input:focus-visible+&]:outline-offset-2 [input:focus-visible+&]:outline-azure-500',
              uploading && 'pointer-events-none opacity-50',
            )}
          >
            {uploading && <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />}
            Elegir archivo
          </label>
        </div>
      </div>
      {error && (
        <Notice tone="error" autoFocus>
          {error}
        </Notice>
      )}
    </section>
  )
}

// ---------------- Lista ----------------

function CvList({ token, cvs, onRemoved }: { token: string; cvs: CvDocument[]; onRemoved: (id: string) => void }) {
  return (
    <section aria-labelledby="tus-archivos">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="tus-archivos" className="text-lg font-semibold text-ink-900">
          Tus archivos
        </h2>
        <p className="text-sm text-ink-500">
          {cvs.length} de {MAX_CVS}
        </p>
      </div>
      <ul className="mt-3 divide-y divide-ink-100 rounded-card bg-white shadow-soft">
        {cvs.map((cv) => (
          <CvRow key={cv.id} token={token} cv={cv} onRemoved={onRemoved} />
        ))}
      </ul>
    </section>
  )
}

function CvRow({ token, cv, onRemoved }: { token: string; cv: CvDocument; onRemoved: (id: string) => void }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const confirmRef = useRef<HTMLButtonElement>(null)
  const removeRef = useRef<HTMLButtonElement>(null)
  const label = `CV subido el ${uploadedFormat.format(new Date(cv.uploadedAt))}`

  useEffect(() => {
    if (confirming) confirmRef.current?.focus()
  }, [confirming])

  async function remove() {
    setBusy(true)
    setError(null)
    try {
      await cvApi.remove(token, cv.id)
      onRemoved(cv.id)
    } catch (err) {
      setError(errorMessage(err, 'No se pudo eliminar el CV. Vuelve a intentarlo.'))
      setBusy(false)
    }
  }

  function cancel() {
    setConfirming(false)
    setError(null)
    requestAnimationFrame(() => removeRef.current?.focus())
  }

  return (
    <li className="flex flex-col gap-3 px-5 py-4 sm:px-6" aria-label={label}>
      <div className="flex items-center gap-4">
        <CvSheet filled className="w-7 drop-shadow-none" />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-ink-900">{label}</p>
          <p className="text-sm text-ink-500">PDF, {formatSize(cv.sizeBytes)}</p>
        </div>
        {!confirming && (
          <Button ref={removeRef} variant="ghost" size="sm" onClick={() => setConfirming(true)} aria-label={`Eliminar ${label}`}>
            Eliminar
          </Button>
        )}
      </div>
      {confirming && (
        <div className="flex flex-col gap-3 rounded-2xl bg-low-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-low-700">Se borrará de Worksy de forma definitiva.</p>
          <div className="flex gap-2">
            <Button ref={confirmRef} size="sm" className="bg-low-700 hover:bg-low-700/90" onClick={() => void remove()} disabled={busy}>
              {busy && <LoaderCircle className="animate-spin" aria-hidden="true" />}
              Eliminar CV
            </Button>
            <Button variant="secondary" size="sm" onClick={cancel} disabled={busy}>
              Cancelar
            </Button>
          </div>
        </div>
      )}
      {error && <Notice tone="error">{error}</Notice>}
    </li>
  )
}

// ---------------- Privacidad ----------------

function PrivacySection({
  token,
  consents,
  onSaved,
}: {
  token: string
  consents: Consents
  onSaved: (consents: Consents) => void
}) {
  const labelId = useId()
  const descId = useId()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const on = consents.shareWithRecruiters

  async function toggle() {
    setSaving(true)
    setError(null)
    try {
      onSaved(await cvApi.updateConsents(token, { shareWithRecruiters: !on, githubLink: consents.githubLink }))
    } catch (err) {
      setError(errorMessage(err, 'No se pudo guardar tu preferencia. Vuelve a intentarlo.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section aria-labelledby="quien-ve-tu-cv" className="rounded-panel bg-navy-900 p-6 text-white sm:p-8">
      <h2 id="quien-ve-tu-cv" className="text-lg font-semibold">
        Quién puede ver tu CV
      </h2>
      <div className="mt-5 flex items-start justify-between gap-6">
        <div>
          <p id={labelId} className="text-base font-medium">
            Compartirlo con las empresas a las que me postule
          </p>
          <p id={descId} className="mt-1 max-w-md text-sm leading-relaxed text-azure-200">
            {on
              ? 'Quien recluta en esas vacantes podrá ver tu CV. Puedes desactivarlo cuando quieras.'
              : 'Ahora solo tú puedes verlo.'}
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={on}
          aria-labelledby={labelId}
          aria-describedby={descId}
          disabled={saving}
          onClick={() => void toggle()}
          className={cn(
            'relative mt-0.5 inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full transition-colors focus-visible:outline-white disabled:cursor-wait',
            on ? 'bg-azure-400' : 'bg-white/20',
          )}
        >
          <span
            aria-hidden="true"
            className={cn('size-5 rounded-full bg-white shadow transition-transform', on ? 'translate-x-6' : 'translate-x-1')}
          />
        </button>
      </div>
      {error && (
        <Notice tone="error" className="mt-5">
          {error}
        </Notice>
      )}
    </section>
  )
}
