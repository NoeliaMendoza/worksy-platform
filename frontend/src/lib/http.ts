import { ApiError } from '@/features/auth/api'

type Options = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** Objeto JSON, o FormData para subir archivos (el navegador pone el Content-Type del multipart). */
  body?: unknown
  /** Token de acceso; se envía como Authorization: Bearer. */
  token?: string
  /** Cabeceras adicionales (p. ej., Idempotency-Key). */
  headers?: Record<string, string>
}

/**
 * Cliente JSON para /api/v1. Convierte los ProblemDetail en ApiError con su código estable,
 * y los fallos de red en ApiError(0, 'network').
 */
export async function apiRequest<T>(path: string, { method = 'GET', body, token, headers: extra }: Options = {}): Promise<T> {
  const isForm = body instanceof FormData
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json', ...extra }
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'network', 'No se pudo conectar con el servidor.')
  }
  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T
  }
  let code = `http.${response.status}`
  let detail = response.statusText
  try {
    const problem = (await response.json()) as { code?: string; detail?: string }
    code = problem.code ?? code
    detail = problem.detail ?? detail
  } catch {
    // Sin cuerpo JSON: se usa el código HTTP.
  }
  throw new ApiError(response.status, code, detail)
}

/** Mensaje para personas: el de la API si existe; si no, uno genérico según el tipo de fallo. */
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return 'No se pudo conectar con el servidor. Revisa tu conexión y vuelve a intentarlo.'
    if (error.status >= 500) return 'El servicio no está disponible ahora. Vuelve a intentarlo en unos minutos.'
    if (error.message) return error.message.endsWith('.') ? error.message : `${error.message}.`
  }
  return fallback
}
