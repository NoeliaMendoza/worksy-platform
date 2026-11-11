import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '@/App'
import type { AuthUser } from '@/features/auth/api'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import { ResetPasswordPage } from '@/features/auth/reset-password-page'
import { RegisterCompanyPage } from './register-company-page'
import { TeamPage } from './team-page'

// Datos sintéticos: dominio reservado .test.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  window.history.replaceState(null, '', '/')
})

afterEach(() => vi.unstubAllGlobals())

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function routes(handlers: Record<string, (init?: RequestInit) => Response>) {
  fetchMock.mockImplementation((url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`
    const handler = Object.entries(handlers).find(([k]) => key.endsWith(k))?.[1]
    return Promise.resolve(handler ? handler(init) : new Response(null, { status: 404 }))
  })
}

const calls = (suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => `${(init as RequestInit | undefined)?.method ?? 'GET'} ${url}`.endsWith(suffix)) as [
    string,
    RequestInit,
  ][]

function withAuth(user: Partial<AuthUser>, children: ReactNode, login = vi.fn()) {
  const value: AuthContextValue = {
    state: {
      status: 'authenticated',
      accessToken: 'jwt-sintetico',
      expiresAt: Date.now() + 900_000,
      user: { id: 'u-1', email: 'admin@andina.test', role: 'COMPANY_ADMIN', companyId: 'c-1', createdAt: '', ...user },
    },
    login,
    logout: vi.fn(),
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

const company = { id: 'c-1', name: 'Andina Cargo', createdAt: '2026-11-10T00:00:00Z' }
const admin = { id: 'u-1', email: 'admin@andina.test', role: 'COMPANY_ADMIN', createdAt: '2026-11-10T00:00:00Z' }

describe('RegisterCompanyPage', () => {
  it('valida los campos y registra la empresa antes de iniciar sesión', async () => {
    routes({ 'POST /api/v1/companies': () => json(201, { company, admin }) })
    const login = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(withAuth({}, <RegisterCompanyPage />, login))

    await user.click(screen.getByRole('button', { name: 'Registrar empresa' }))
    expect(screen.getByLabelText('Nombre de la empresa')).toHaveAccessibleDescription(/al menos 2 caracteres/)
    expect(fetchMock).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText('Nombre de la empresa'), 'Andina Cargo')
    await user.type(screen.getByLabelText('Tu correo de trabajo'), 'admin@andina.test')
    await user.type(screen.getByLabelText('Contraseña'), 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Registrar empresa' }))

    await waitFor(() => expect(login).toHaveBeenCalledWith('admin@andina.test', 'frase-sintetica-larga'))
    expect(JSON.parse(calls('POST /api/v1/companies')[0]![1].body as string)).toEqual({
      companyName: 'Andina Cargo',
      email: 'admin@andina.test',
      password: 'frase-sintetica-larga',
    })
  })

  it('muestra el error de la API (correo ya registrado)', async () => {
    routes({
      'POST /api/v1/companies': () => json(409, { code: 'user.email.taken', detail: 'Ya existe una cuenta con ese correo' }),
    })
    const user = userEvent.setup()
    render(withAuth({}, <RegisterCompanyPage />))
    await user.type(screen.getByLabelText('Nombre de la empresa'), 'Andina Cargo')
    await user.type(screen.getByLabelText('Tu correo de trabajo'), 'admin@andina.test')
    await user.type(screen.getByLabelText('Contraseña'), 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Registrar empresa' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe una cuenta con ese correo.')
  })
})

describe('TeamPage', () => {
  it('la administración ve su equipo e invita con su token', async () => {
    const recruiter = { id: 'u-2', email: 'recluta@andina.test', role: 'RECRUITER', createdAt: '2026-11-10T00:00:00Z' }
    routes({
      'GET /api/v1/companies/me': () => json(200, company),
      'GET /api/v1/companies/me/members': () => json(200, [admin]),
      'POST /api/v1/companies/me/members': () => json(201, recruiter),
    })
    const user = userEvent.setup()
    render(withAuth({}, <TeamPage />))

    expect(await screen.findByText('Personas de Andina Cargo que usan Worksy.')).toBeInTheDocument()
    const list = screen.getByRole('list')
    expect(within(list).getByText('admin@andina.test')).toBeInTheDocument()
    expect(within(list).getByText('Administración')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Correo'), 'recluta@andina.test')
    await user.click(screen.getByRole('button', { name: 'Enviar invitación' }))

    const confirmation = await screen.findByText(/Invitación enviada a recluta@andina\.test/)
    expect(confirmation.closest('[role="status"]')).not.toBeNull()
    expect(within(screen.getByRole('list')).getByText('recluta@andina.test')).toBeInTheDocument()
    const [, init] = calls('POST /api/v1/companies/me/members')[0]!
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer jwt-sintetico')
  })

  it('un reclutador ve el equipo pero no puede invitar', async () => {
    routes({
      'GET /api/v1/companies/me': () => json(200, company),
      'GET /api/v1/companies/me/members': () => json(200, [admin]),
    })
    render(withAuth({ role: 'RECRUITER' }, <TeamPage />))
    expect(await screen.findByText(/Solo la administración de la empresa puede invitar/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar invitación' })).not.toBeInTheDocument()
  })

  it('si falla la carga ofrece reintentar', async () => {
    routes({ 'GET /api/v1/companies/me': () => json(503, {}) })
    render(withAuth({}, <TeamPage />))
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cargar el equipo')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})

describe('Activar cuenta desde la invitación', () => {
  it('usa el texto de activación y avisa si la invitación caducó', async () => {
    window.history.replaceState(null, '', '/restablecer?invitacion=1&token=secreto-sintetico')
    routes({
      'POST /api/v1/auth/password/reset': () =>
        new Response(JSON.stringify({ code: 'password.reset.invalid' }), { status: 400 }),
    })
    const user = userEvent.setup()
    render(<ResetPasswordPage />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Activa tu cuenta')
    await user.type(screen.getByLabelText('Contraseña nueva'), 'frase-sintetica-larga')
    await user.type(screen.getByLabelText('Repite la contraseña'), 'frase-sintetica-larga')
    await user.click(screen.getByRole('button', { name: 'Activar cuenta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La invitación no es válida o ya caducó')
    expect(screen.queryByRole('link', { name: 'Pedir un enlace nuevo' })).not.toBeInTheDocument()
  })
})

describe('Rutas de la empresa', () => {
  function signedInAs(role: string) {
    const tokens = {
      accessToken: 'jwt-sintetico',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { id: 'u-1', email: 'admin@andina.test', role, companyId: 'c-1', createdAt: '' },
    }
    routes({
      'POST /api/v1/auth/refresh': () => json(200, tokens),
      'GET /api/v1/auth/providers': () => json(200, []),
      'GET /api/v1/companies/me': () => json(200, company),
      'GET /api/v1/companies/me/members': () => json(200, [admin]),
    })
  }

  it('/equipo muestra el equipo y lo marca en el menú', async () => {
    window.history.replaceState(null, '', '/equipo')
    signedInAs('COMPANY_ADMIN')
    render(<App />)
    expect(await screen.findByRole('heading', { level: 1, name: 'Tu equipo' })).toBeInTheDocument()
    const nav = screen.getAllByRole('navigation', { name: 'Principal' })[0]!
    expect(within(nav).getByRole('link', { name: 'Equipo' })).toHaveAttribute('aria-current', 'page')
  })

  it('una sección sin pantalla avisa con claridad', async () => {
    window.history.replaceState(null, '', '/mensajes')
    signedInAs('RECRUITER')
    render(<App />)
    expect(await screen.findByText('Esta sección todavía no está disponible')).toBeInTheDocument()
    const nav = screen.getAllByRole('navigation', { name: 'Principal' })[0]!
    expect(within(nav).getByRole('link', { name: 'Mensajes' })).toHaveAttribute('aria-current', 'page')
  })

  it('sin sesión, /registro-empresa abre el registro y el login enlaza a él', async () => {
    routes({ 'POST /api/v1/auth/refresh': () => json(401, { code: 'auth.refresh.invalid' }) })
    render(<App />)
    const link = await screen.findByRole('link', { name: 'Regístrala' })
    expect(link).toHaveAttribute('href', '/registro-empresa')
    await userEvent.setup().click(link)
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Registra tu empresa')
  })
})
