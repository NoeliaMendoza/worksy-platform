/**
 * Cliente HTTP de /api/v1/auth. El token de acceso vive solo en memoria;
 * el de refresco lo gestiona el navegador en una cookie HttpOnly que JavaScript no puede leer.
 */

export type Role = 'CANDIDATE' | 'RECRUITER' | 'COMPANY_ADMIN' | 'PLATFORM_ADMIN'

export interface AuthUser {
  id: string
  email: string
  role: Role
  companyId: string | null
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: AuthUser
}

export interface Provider {
  id: 'google' | 'linkedin' | 'github'
  name: string
  authorizationUrl: string
}

/** Error de la API en formato ProblemDetail, o de red (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const BASE = '/api/v1/auth'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      ...init,
      credentials: 'same-origin',
      headers: { Accept: 'application/json, application/problem+json', ...init.headers },
    })
  } catch {
    throw new ApiError(0, 'network', 'No se pudo conectar con el servidor.')
  }
  if (response.ok) {
    return (response.status === 204 || response.status === 202 ? undefined : await response.json()) as T
  }
  let code = `http.${response.status}`
  let detail = response.statusText
  try {
    const problem = (await response.json()) as { code?: string; detail?: string }
    code = problem.code ?? code
    detail = problem.detail ?? detail
  } catch {
    // Sin cuerpo JSON (p. ej., proxy caído): se usa el código HTTP.
  }
  throw new ApiError(response.status, code, detail)
}

export function login(email: string, password: string) {
  return request<TokenResponse>('/login', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ email, password }),
  })
}

let refreshInFlight: Promise<TokenResponse> | null = null

/**
 * Renueva la sesión con la cookie de refresco. Las llamadas simultáneas comparten una sola
 * petición: dos renovaciones con el mismo token harían que la API lo tome como reutilizado
 * y cierre la sesión (le pasa, por ejemplo, al modo estricto de React en desarrollo).
 */
export function refresh() {
  refreshInFlight ??= request<TokenResponse>('/refresh', { method: 'POST' }).finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

export function logout() {
  return request<void>('/logout', { method: 'POST' })
}

/** Proveedores externos habilitados en la API. Si la consulta falla, no se muestran botones. */
export async function providers(): Promise<Provider[]> {
  try {
    const list = await request<Provider[]>('/providers')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

export function forgotPassword(email: string) {
  return request<void>('/password/forgot', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ email }) })
}

export function resetPassword(token: string, password: string) {
  return request<void>('/password/reset', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ token, password }),
  })
}

const NETWORK = 'No se pudo conectar con el servidor. Revisa tu conexión y vuelve a intentarlo.'

/** Texto para personas a partir del código estable de la API. */
export function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'auth.credentials.invalid') return 'El correo o la contraseña no son correctos.'
    if (error.status === 0) return NETWORK
    if (error.status === 429) return 'Demasiados intentos. Espera un momento y vuelve a intentarlo.'
    if (error.status >= 500 || error.status === 404)
      return 'El servicio de acceso no está disponible ahora. Vuelve a intentarlo en unos minutos.'
    if (error.status === 400) return 'Revisa el correo y la contraseña.'
  }
  return 'No se pudo iniciar sesión. Vuelve a intentarlo.'
}

export function resetErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'password.reset.invalid') return 'El enlace no es válido o ya caducó. Pide uno nuevo.'
    if (error.code === 'user.password.invalid') return error.message || 'La contraseña no cumple los requisitos.'
    if (error.status === 0) return NETWORK
  }
  return 'No se pudo cambiar la contraseña. Vuelve a intentarlo.'
}

/** Motivos con los que la API devuelve al frontend tras un inicio de sesión externo fallido (?acceso=). */
export function externalLoginMessage(reason: string | null): string | null {
  if (reason === 'cancelado') return 'Cancelaste el inicio de sesión con tu cuenta externa.'
  if (reason === 'correo-no-verificado')
    return 'Tu cuenta externa no tiene un correo verificado. Verifícalo con el proveedor o entra con tu correo y contraseña.'
  if (reason === 'error') return 'No se pudo iniciar sesión con tu cuenta externa. Vuelve a intentarlo.'
  return null
}
