import { ExternalLink, FileSearch, RefreshCw, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { useResource } from '@/lib/use-resource'
import { cvApi, type CvDocument } from './api'

export function CvPreview({ token, cv, onClose, onReady }: { token: string; cv: CvDocument | null; onClose: () => void; onReady?: () => void }) {
  return <section aria-labelledby="vista-previa-cv" className="overflow-hidden rounded-card border border-ink-100 bg-white shadow-soft">
    <div className="flex items-start justify-between gap-3 border-b border-ink-100 px-4 py-3">
      <div className="min-w-0"><h2 id="vista-previa-cv" className="text-sm font-semibold">Vista previa</h2>
        <p className="mt-1 break-words text-sm text-ink-500">{cv ? cv.originalName || 'CV en PDF' : 'Revisa el archivo que vas a compartir.'}</p></div>
      {cv && <Button variant="ghost" size="sm" aria-label="Cerrar vista previa" onClick={onClose}><X aria-hidden="true" /></Button>}
    </div>
    {cv ? <PreviewDocument key={cv.id} token={token} cv={cv} onReady={onReady} /> : <div className="flex flex-col items-center justify-center gap-3 px-4 py-8 text-center">
      <span className="grid size-10 place-items-center rounded-lg bg-azure-100 text-navy-700"><FileSearch className="size-5" aria-hidden="true" /></span>
      <p className="text-sm font-medium">Elige un archivo para revisarlo</p>
      <p className="max-w-xs text-sm leading-relaxed text-ink-500">Pulsa «Vista previa» en uno de tus CV. El documento se abrirá aquí.</p>
    </div>}
  </section>
}

function PreviewDocument({ token, cv, onReady }: { token: string; cv: CvDocument; onReady?: () => void }) {
  const { state, retry } = useResource(() => cvApi.link(token, cv.id), `${token}:${cv.id}`, 'No se pudo preparar la vista previa.')
  useEffect(() => { if (state.status === 'ready') onReady?.() }, [state.status, onReady])
  if (state.status === 'loading') return <p role="status" className="p-6 text-sm text-ink-700">Preparando el PDF…</p>
  if (state.status === 'error') return <div role="alert" className="p-5 text-sm text-ink-700"><p>{state.message}</p>
    <Button variant="secondary" size="sm" className="mt-4" onClick={retry}>Reintentar vista previa</Button></div>
  return <SignedPreview key={state.data.url + state.data.expiresAt} url={state.data.url} expiresAt={state.data.expiresAt} name={cv.originalName || 'CV en PDF'} onRefresh={retry} />
}

function SignedPreview({ url, expiresAt, name, onRefresh }: { url: string; expiresAt: string; name: string; onRefresh: () => void }) {
  const [expired, setExpired] = useState(() => Date.parse(expiresAt) <= Date.now())
  useEffect(() => {
    // El servidor emite enlaces de 5 min; limita también el temporizador ante un reloj local desajustado.
    const delay = Math.min(5 * 60_000, Math.max(0, Date.parse(expiresAt) - Date.now()))
    const timer = window.setTimeout(() => setExpired(true), delay)
    function check() { if (Date.now() >= Date.parse(expiresAt)) setExpired(true) }
    document.addEventListener('visibilitychange', check)
    return () => { window.clearTimeout(timer); document.removeEventListener('visibilitychange', check) }
  }, [expiresAt])
  return <>
    <div className="flex flex-wrap items-center gap-2 bg-azure-50 px-4 py-3">
      {!expired && <Button asChild variant="secondary" size="sm"><a href={url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer"><ExternalLink aria-hidden="true" />Abrir PDF</a></Button>}
      <Button variant="ghost" size="sm" onClick={onRefresh}><RefreshCw aria-hidden="true" />Actualizar vista previa</Button>
    </div>
    {expired ? <p role="status" className="px-5 py-8 text-sm text-ink-700">El enlace temporal venció. Actualiza la vista previa para volver a abrir tu CV.</p>
      : <iframe src={`${url}#view=FitH&navpanes=0`} title={`Vista previa de ${name}`} referrerPolicy="no-referrer" className="h-[65vh] min-h-80 w-full border-0 bg-ink-100 xl:h-[640px]" />}
    <p className="border-t border-ink-100 px-5 py-4 text-xs leading-5 text-ink-500">Si el visor no carga, prueba «Abrir PDF» o actualiza la vista previa. Puedes guardar una copia desde el visor de tu navegador. El enlace de lectura dura 5 minutos; evita compartirlo.</p>
  </>
}
