import { apiRequest } from '@/lib/http'

/** Debe coincidir con la API (PdfFile.MAX_BYTES y CvService.MAX_PER_CANDIDATE). */
export const MAX_CV_BYTES = 5 * 1024 * 1024
export const MAX_CVS = 5

export interface CvDocument {
  id: string
  sizeBytes: number
  status: 'UPLOADED'
  uploadedAt: string
}

export interface Consents {
  shareWithRecruiters: boolean
  githubLink: boolean
  /** null si la persona nunca decidió. */
  updatedAt: string | null
}

const base = '/candidates/me'

export const cvApi = {
  list: (token: string) => apiRequest<CvDocument[]>(`${base}/cv`, { token }),
  upload: (token: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiRequest<CvDocument>(`${base}/cv`, { method: 'POST', body: form, token })
  },
  remove: (token: string, id: string) => apiRequest<void>(`${base}/cv/${id}`, { method: 'DELETE', token }),
  consents: (token: string) => apiRequest<Consents>(`${base}/consents`, { token }),
  updateConsents: (token: string, body: Pick<Consents, 'shareWithRecruiters' | 'githubLink'>) =>
    apiRequest<Consents>(`${base}/consents`, { method: 'PUT', body, token }),
}

/**
 * Revisión previa en el navegador para avisar antes de subir. La API vuelve a validar el contenido real.
 * Devuelve el mensaje a mostrar, o null si el archivo parece válido.
 */
export function precheck(file: File): string | null {
  const looksPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!looksPdf) return 'Ese archivo no es un PDF. Exporta tu CV como PDF y súbelo de nuevo.'
  if (file.size === 0) return 'El archivo está vacío. Elige otro.'
  if (file.size > MAX_CV_BYTES) return `El archivo pesa ${formatSize(file.size)} y el máximo es 5 MB. Reduce su tamaño y vuelve a intentarlo.`
  return null
}

const kb = new Intl.NumberFormat('es', { maximumFractionDigits: 0 })
const mb = new Intl.NumberFormat('es', { maximumFractionDigits: 1 })

export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${kb.format(Math.max(1, bytes / 1024))} KB`
  return `${mb.format(bytes / (1024 * 1024))} MB`
}
