import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from './auth-context'
import { RegisterPage } from './register-page'

// Datos sintéticos: dominio reservado .test.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  window.history.replaceState(null, '', '/registro')
})

afterEach(() => {
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function renderWith(login = vi.fn().mockResolvedValue(undefined)) {
  const value: AuthContextValue = { state: { status: 'anonymous' }, login, logout: vi.fn() }
  render(
    <AuthContext.Provider value={value}>
      <RegisterPage />
    </AuthContext.Provider>,
  )
  return login
}

function routes(handlers: Record<string, () => Response>) {
  fetchMock.mockImplementation((url: string) => {
    const handler = Object.entries(handlers).find(([path]) => url.endsWith(path))?.[1]
    return Promise.resolve(handler ? handler() : new Response(null, { status: 404 }))
  })
}

describe('RegisterPage (candidatos)', () => {
  it('valida, crea la cuenta de candidato e inicia sesión con ella', async () => {
    routes({
      '/api/v1/auth/register': () =>
        json(201, { id: 'u-1', email: 'ana@ejemplo.test', role: 'CANDIDATE', companyId: null, createdAt: '' }),
    })
    const user = userEvent.setup()
    const login = renderWith()

    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(screen.getByLabelText('Correo')).toHaveAccessibleDescription('Escribe tu correo.')

    await user.type(screen.getByLabelText('Correo'), 'ana@ejemplo.test')
    await user.type(screen.getByLabelText('Contraseña'), 'corta')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(/entre 10 y 64 caracteres/)
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('/register'), expect.anything())

    await user.clear(screen.getByLabelText('Contraseña'))
    await user.type(screen.getByLabelText('Contraseña'), 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() => expect(login).toHaveBeenCalledWith('ana@ejemplo.test', 'frase-sintetica-larga'))
    const [, init] = fetchMock.mock.calls.find(([url]) => (url as string).endsWith('/register')) as [string, RequestInit]
    expect(JSON.parse(init.body as string)).toEqual({ email: 'ana@ejemplo.test', password: 'frase-sintetica-larga' })
  })

  it('muestra el error de la API si el correo ya tiene cuenta', async () => {
    routes({
      '/api/v1/auth/register': () => json(409, { code: 'user.email.taken', detail: 'Ya existe una cuenta con ese correo' }),
    })
    const user = userEvent.setup()
    renderWith()
    await user.type(screen.getByLabelText('Correo'), 'ana@ejemplo.test')
    await user.type(screen.getByLabelText('Contraseña'), 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe una cuenta con ese correo.')
  })

  it('ofrece registrarse con proveedores externos y volver a iniciar sesión', async () => {
    routes({
      '/api/v1/auth/providers': () =>
        json(200, [{ id: 'google', name: 'Google', authorizationUrl: '/api/v1/auth/oauth2/authorization/google' }]),
    })
    renderWith()
    expect(await screen.findByRole('link', { name: 'Continuar con Google' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Inicia sesión' })).toHaveAttribute('href', '/')
  })
})
