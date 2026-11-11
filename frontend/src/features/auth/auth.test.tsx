import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, loginErrorMessage, refresh } from './api'
import { AuthProvider } from './auth-provider'
import { ForgotPasswordPage } from './forgot-password-page'
import { LoginPage } from './login-page'
import { ResetPasswordPage } from './reset-password-page'

// Datos sintéticos: dominio reservado .test.
const tokens = {
  accessToken: 'jwt-sintetico',
  tokenType: 'Bearer',
  expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
  user: { id: 'u-1', email: 'reclutadora@ejemplo.test', role: 'RECRUITER', companyId: 'c-1', createdAt: '2026-11-09T00:00:00Z' },
}

function json(status: number, body: unknown, type = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } })
}

const problem = (status: number, code: string) =>
  json(status, { status, code, detail: 'detalle' }, 'application/problem+json')

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  window.history.replaceState(null, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function routes(handlers: Record<string, () => Response>) {
  fetchMock.mockImplementation((url: string) => {
    const handler = Object.entries(handlers).find(([path]) => url.endsWith(path))?.[1]
    return Promise.resolve(handler ? handler() : new Response(null, { status: 404 }))
  })
}

function calls(path: string) {
  return fetchMock.mock.calls.filter(([url]) => (url as string).endsWith(path)) as [string, RequestInit][]
}

const anonymous = { '/refresh': () => problem(401, 'auth.refresh.invalid') }

describe('api', () => {
  it('convierte un ProblemDetail en ApiError con su código', async () => {
    routes(anonymous)
    await expect(refresh()).rejects.toMatchObject({ status: 401, code: 'auth.refresh.invalid' })
  })

  it('comparte una sola renovación entre llamadas simultáneas', async () => {
    routes({ '/refresh': () => json(200, tokens) })
    const [a, b] = await Promise.all([refresh(), refresh()])
    expect(a).toEqual(b)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('traduce los errores a mensajes para personas', () => {
    expect(loginErrorMessage(new ApiError(401, 'auth.credentials.invalid', ''))).toBe(
      'El correo o la contraseña no son correctos.',
    )
    expect(loginErrorMessage(new ApiError(0, 'network', ''))).toMatch(/No se pudo conectar/)
    expect(loginErrorMessage(new ApiError(503, 'http.503', ''))).toMatch(/no está disponible/)
  })
})

describe('LoginPage', () => {
  function renderLogin() {
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>,
    )
  }

  it('valida los campos antes de llamar a la API', async () => {
    routes(anonymous)
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    const email = screen.getByLabelText('Correo')
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveAccessibleDescription('Escribe tu correo.')
    expect(email).toHaveFocus()

    await user.type(email, 'no-es-correo')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(email).toHaveAccessibleDescription(/correo válido/)
    expect(calls('/login')).toHaveLength(0)
  })

  it('muestra un error genérico, vacía la contraseña y lleva el foco al aviso', async () => {
    routes({ ...anonymous, '/login': () => problem(401, 'auth.credentials.invalid') })
    const user = userEvent.setup()
    renderLogin()

    await user.type(screen.getByLabelText('Correo'), 'reclutadora@ejemplo.test')
    await user.type(screen.getByLabelText('Contraseña'), 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('El correo o la contraseña no son correctos.')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('')
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('envía las credenciales y permite mostrar la contraseña', async () => {
    routes({ ...anonymous, '/login': () => json(200, tokens) })
    const user = userEvent.setup()
    renderLogin()

    const password = screen.getByLabelText('Contraseña')
    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(password).toHaveAttribute('type', 'text')

    await user.type(screen.getByLabelText('Correo'), '  reclutadora@ejemplo.test ')
    await user.type(password, 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    await waitFor(() => expect(calls('/login')).toHaveLength(1))
    expect(JSON.parse(calls('/login')[0]![1].body as string)).toEqual({
      email: 'reclutadora@ejemplo.test',
      password: 'frase-sintetica-larga',
    })
  })

  it('muestra solo los proveedores habilitados, como enlaces a la API', async () => {
    routes({
      ...anonymous,
      '/providers': () =>
        json(200, [
          { id: 'google', name: 'Google', authorizationUrl: '/api/v1/auth/oauth2/authorization/google' },
          { id: 'github', name: 'GitHub', authorizationUrl: '/api/v1/auth/oauth2/authorization/github' },
        ]),
    })
    renderLogin()
    const google = await screen.findByRole('link', { name: 'Continuar con Google' })
    expect(google).toHaveAttribute('href', '/api/v1/auth/oauth2/authorization/google')
    expect(screen.getByRole('link', { name: 'Continuar con GitHub' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Continuar con LinkedIn' })).not.toBeInTheDocument()
  })

  it('sin proveedores no muestra botones ni el separador', async () => {
    routes(anonymous)
    renderLogin()
    await waitFor(() => expect(calls('/providers')).toHaveLength(1))
    expect(screen.queryByRole('list', { name: 'Iniciar sesión con otra cuenta' })).not.toBeInTheDocument()
    expect(screen.queryByText('o continúa con')).not.toBeInTheDocument()
  })

  it('explica por qué falló el inicio de sesión externo', async () => {
    window.history.replaceState(null, '', '/?acceso=correo-no-verificado')
    routes(anonymous)
    renderLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent(/no tiene un correo verificado/)
    await waitFor(() => expect(window.location.search).toBe(''))
  })

  it('enlaza a recuperar la contraseña', () => {
    routes(anonymous)
    renderLogin()
    expect(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' })).toHaveAttribute('href', '/recuperar')
  })
})

describe('ForgotPasswordPage', () => {
  it('pide el enlace y confirma sin revelar si la cuenta existe', async () => {
    routes({ '/password/forgot': () => new Response(null, { status: 202 }) })
    const user = userEvent.setup()
    render(<ForgotPasswordPage />)

    await user.type(screen.getByLabelText('Correo'), 'alguien@ejemplo.test')
    await user.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    const status = await screen.findByRole('status')
    expect(status).toHaveTextContent('Si hay una cuenta con alguien@ejemplo.test')
    expect(JSON.parse(calls('/password/forgot')[0]![1].body as string)).toEqual({ email: 'alguien@ejemplo.test' })
  })
})

describe('ResetPasswordPage', () => {
  it('sin token pide abrir el enlace del correo', () => {
    render(<ResetPasswordPage />)
    expect(screen.getByText('Falta el enlace')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pedir un enlace nuevo' })).toHaveAttribute('href', '/recuperar')
  })

  it('valida longitud y coincidencia, y cambia la contraseña', async () => {
    window.history.replaceState(null, '', '/restablecer?token=secreto-sintetico')
    routes({ '/password/reset': () => new Response(null, { status: 204 }) })
    const user = userEvent.setup()
    render(<ResetPasswordPage />)

    await user.type(screen.getByLabelText('Contraseña nueva'), 'corta')
    await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
    expect(screen.getByLabelText('Contraseña nueva')).toHaveAccessibleDescription(/Debe tener entre 10 y 128/)

    await user.clear(screen.getByLabelText('Contraseña nueva'))
    await user.type(screen.getByLabelText('Contraseña nueva'), 'nueva-frase-larga')
    await user.type(screen.getByLabelText('Repite la contraseña'), 'otra-frase-larga')
    await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
    expect(screen.getByLabelText('Repite la contraseña')).toHaveAccessibleDescription('Las contraseñas no coinciden.')

    await user.clear(screen.getByLabelText('Repite la contraseña'))
    await user.type(screen.getByLabelText('Repite la contraseña'), 'nueva-frase-larga')
    await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tu contraseña se cambió.')
    expect(JSON.parse(calls('/password/reset')[0]![1].body as string)).toEqual({
      token: 'secreto-sintetico',
      password: 'nueva-frase-larga',
    })
    expect(window.location.search).toBe('')
  })

  it('con un enlace caducado ofrece pedir otro', async () => {
    window.history.replaceState(null, '', '/restablecer?token=viejo')
    routes({ '/password/reset': () => problem(400, 'password.reset.invalid') })
    const user = userEvent.setup()
    render(<ResetPasswordPage />)

    await user.type(screen.getByLabelText('Contraseña nueva'), 'nueva-frase-larga')
    await user.type(screen.getByLabelText('Repite la contraseña'), 'nueva-frase-larga')
    await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('ya caducó')
    expect(screen.getByRole('link', { name: 'Pedir un enlace nuevo' })).toBeInTheDocument()
  })
})
