import { PageHeader } from '@/components/page-header'
import { Eye, LoaderCircle, ShieldCheck, Upload } from 'lucide-react'
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
import { CvPreview } from './cv-preview'
import { Badge } from '@/components/ui/badge'

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
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const previewRef = useRef<HTMLDivElement>(null)
  const settlePreview = useCallback(() => {
    if (document.activeElement === previewRef.current && window.matchMedia?.('(min-width: 1280px)').matches !== true) {
      previewRef.current?.scrollIntoView?.({ block: 'start', behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
    }
  }, [])

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
    <div className="app-page">
      <PageHeader title="Mi CV" description="Gestiona tus archivos, revisa su contenido y decide quién puede verlos." />

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
        <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
          <div className="flex min-w-0 flex-col gap-4">
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
              selectedId={selectedId}
              onPreview={(id) => {
                setSelectedId(id)
                requestAnimationFrame(() => {
                  previewRef.current?.focus({ preventScroll: true })
                  settlePreview()
                })
              }}
              onRemoved={(id) => {
                update((r) => ({ cvs: r.cvs.filter((c) => c.id !== id) }))
                setAnnouncement('CV eliminado.')
              }}
            />
          )}
          <p className="text-xs leading-5 text-ink-500">Eliges un CV al postular. Subir otro archivo no sustituye el CV de las postulaciones que ya enviaste.</p>
          <PrivacySection
            token={token}
            consents={load.consents}
            onSaved={(consents) => {
              update(() => ({ consents }))
              setAnnouncement(consents.shareWithRecruiters ? 'Las empresas a las que te postules podrán ver tu CV.' : 'Tu CV ya no se comparte.')
            }}
          />
          </div>
          <div ref={previewRef} tabIndex={-1} className="min-w-0 scroll-mt-4 rounded-card xl:sticky xl:top-6">
            <CvPreview token={token} cv={load.cvs.find((cv) => cv.id === selectedId) ?? null} onReady={settlePreview} onClose={() => {
              const previous = selectedId
              setSelectedId(null)
              requestAnimationFrame(() => document.getElementById(`preview-${previous}`)?.focus())
            }} />
          </div>
        </div>
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
      <section aria-labelledby={`${inputId}-full`} className="flex items-center gap-4 rounded-card border border-ink-100 bg-white p-4">
        <CvSheet filled className="w-9" />
        <div>
          <h2 id={`${inputId}-full`} className="text-base font-semibold text-ink-900">
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
          'relative flex flex-wrap items-center gap-4 rounded-card border border-dashed border-azure-300 bg-white p-4 shadow-soft ring-2 ring-transparent transition-[box-shadow,background-color]',
          dragging && 'bg-azure-50 ring-azure-500',
        )}
      >
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-azure-100 text-azure-600"><Upload className="size-5" aria-hidden="true" /></span>
        <div className="flex min-w-0 flex-1 flex-col items-start gap-3">
          {uploading ? (
            <div>
              <p className="text-base font-semibold text-ink-900">Subiendo tu CV…</p>
              <p className="mt-1 truncate text-sm text-ink-500" title={uploading}>
                {uploading}
              </p>
            </div>
          ) : (
            <div>
              <p className="text-base font-semibold text-ink-900">{hasCv ? 'Añadir otro CV' : 'Arrastra tu CV aquí'}</p>
              <p id={hintId} className="mt-1 text-sm text-ink-500">
                PDF · Máximo 5 MB por archivo · Hasta {MAX_CVS} archivos.
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
              'inline-flex h-10 cursor-pointer items-center gap-2 rounded-md bg-navy-900 px-5 text-sm font-semibold text-white transition-colors hover:bg-navy-700',
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

function CvList({ token, cvs, selectedId, onPreview, onRemoved }: { token: string; cvs: CvDocument[]; selectedId: string | null; onPreview: (id: string) => void; onRemoved: (id: string) => void }) {
  return (
    <section aria-labelledby="tus-archivos">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="tus-archivos" className="text-base font-semibold text-ink-900">
          Tus archivos
        </h2>
        <p className="text-sm text-ink-500">
          {cvs.length} de {MAX_CVS}
        </p>
      </div>
      <ul className="mt-3 divide-y divide-ink-100 overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft">
        {cvs.map((cv) => (
          <CvRow key={cv.id} token={token} cv={cv} selected={selectedId === cv.id} onPreview={onPreview} onRemoved={onRemoved} />
        ))}
      </ul>
    </section>
  )
}

function CvRow({ token, cv, selected, onPreview, onRemoved }: { token: string; cv: CvDocument; selected: boolean; onPreview: (id: string) => void; onRemoved: (id: string) => void }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const confirmRef = useRef<HTMLButtonElement>(null)
  const removeRef = useRef<HTMLButtonElement>(null)
  const uploadedLabel = `CV subido el ${uploadedFormat.format(new Date(cv.uploadedAt))}`
  const label = cv.originalName ? `${cv.originalName}, subido el ${uploadedFormat.format(new Date(cv.uploadedAt))}` : uploadedLabel

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
    <li className={cn('flex flex-col gap-3 px-4 py-4', selected && 'bg-azure-50')} aria-label={label}>
      <div className="flex items-start gap-3">
        <CvSheet filled className="w-7 drop-shadow-none" />
        <div className="min-w-0 flex-1">
          <p className="break-words text-sm font-semibold text-ink-900">{cv.originalName || uploadedLabel}</p>
          <p className="text-sm text-ink-500">PDF, {formatSize(cv.sizeBytes)}</p>
          {cv.originalName && <p className="mt-1 text-xs leading-5 text-ink-500">Subido el {uploadedFormat.format(new Date(cv.uploadedAt))}</p>}
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button id={`preview-${cv.id}`} variant="secondary" size="sm" aria-label={`Vista previa de ${label}`} aria-pressed={selected} onClick={() => onPreview(cv.id)}><Eye aria-hidden="true" />Vista previa</Button>
        {!confirming && (
          <Button ref={removeRef} variant="ghost" size="sm" onClick={() => setConfirming(true)} aria-label={`Eliminar ${label}`}>
            Eliminar
          </Button>
        )}
      </div>
      {confirming && (
        <div className="flex flex-col gap-3 rounded-card bg-low-100 p-4 sm:flex-row sm:items-center sm:justify-between">
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
      {confirming && <p className="text-xs leading-5 text-ink-700">Si este archivo se usa en una postulación, Worksy impedirá eliminarlo. No necesitas retirarte de un proceso para subir otro CV.</p>}
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
    <section aria-labelledby="quien-ve-tu-cv" className="rounded-card border border-ink-100 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="quien-ve-tu-cv" className="flex items-center gap-2 text-base font-semibold"><ShieldCheck className="size-4 text-navy-700" aria-hidden="true" />Privacidad del CV</h2>
        <Badge tone={on ? 'high' : 'neutral'}>{on ? 'Compartido al postular' : 'Sin compartir'}</Badge>
      </div>
      <div className="mt-4 flex items-start justify-between gap-4">
        <div>
          <p id={labelId} className="text-sm font-medium text-ink-900">
            Compartirlo con las empresas a las que me postule
          </p>
          <p id={descId} className="mt-1 max-w-md text-sm leading-relaxed text-ink-700">
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
            'relative mt-0.5 inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full transition-colors disabled:cursor-wait disabled:opacity-50',
            on ? 'bg-azure-600' : 'bg-ink-500',
          )}
        >
          <span
            aria-hidden="true"
            className={cn('size-5 rounded-full bg-white shadow transition-transform', on ? 'translate-x-6' : 'translate-x-1')}
          />
        </button>
      </div>
      <details className="mt-4 border-t border-ink-100 pt-3 text-xs leading-5 text-ink-500">
        <summary className="cursor-pointer font-medium text-ink-700">¿Qué ocurre si dejo de compartirlo?</summary>
        <p className="mt-2">Las empresas no podrán solicitar nuevos accesos a tu CV. Los enlaces temporales ya emitidos pueden funcionar hasta su vencimiento, y esto no elimina copias que alguien haya descargado.</p>
      </details>
      {error && (
        <Notice tone="error" className="mt-5">
          {error}
        </Notice>
      )}
    </section>
  )
}
