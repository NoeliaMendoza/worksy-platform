import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '@/App'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import type { CompanyApplication } from './api'
import { CandidatesPage } from './candidates-page'

// Datos sintéticos: dominio reservado .test.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** Respuestas por prefijo de "MÉTODO ruta" (sin la consulta). */
function routes(handlers: Record<string, (url: URL) => Response>) {
  fetchMock.mockImplementation((raw: string, init?: RequestInit) => {
    const url = new URL(raw, 'http://localhost')
    const key = `${init?.method ?? 'GET'} ${url.pathname}`
    const handler = Object.entries(handlers).find(([k]) => key === k)?.[1]
    return Promise.resolve(handler ? handler(url) : new Response(null, { status: 404 }))
  })
}

const searches = () =>
  fetchMock.mock.calls.map(([raw]) => new URL(raw as string, 'http://localhost'))
    .filter((u) => u.pathname === '/api/v1/companies/me/applications')

const jose: CompanyApplication = {
  applicationId: 'app-1', vacancyId: 'vac-1', vacancyTitle: 'Backend Java', vacancyStatus: 'PUBLISHED',
  appliedAt: '2026-11-18T10:00:00Z', shared: true, candidateName: 'José Pérez', candidateEmail: 'jose@ejemplo.test',
  cvId: 'cv-1', stage: 'REVIEW', screeningStatus: 'COMPLETED', screeningFailureCode: null,
  nextInterviewAt: '2026-11-25T15:00:00Z',
}
const hidden: CompanyApplication = {
  applicationId: 'app-2', vacancyId: 'vac-2', vacancyTitle: 'Analista de datos', vacancyStatus: 'PUBLISHED',
  appliedAt: '2026-11-19T10:00:00Z', shared: false, candidateName: null, candidateEmail: null, cvId: null,
  stage: null, screeningStatus: null, screeningFailureCode: null, nextInterviewAt: null,
}
const vacancies = [
  { id: 'vac-1', title: 'Backend Java', status: 'PUBLISHED' },
  { id: 'vac-2', title: 'Analista de datos', status: 'PUBLISHED' },
  { id: 'vac-3', title: 'Borrador interno', status: 'DRAFT' },
]

function renderPage(applicationId?: string) {
  const value: AuthContextValue = {
    state: {
      status: 'authenticated', accessToken: 'jwt-sintetico', expiresAt: Date.now() + 900_000,
      user: { id: 'u-1', email: 'admin@ejemplo.test', role: 'COMPANY_ADMIN', companyId: 'c-1', createdAt: '' },
    },
    login: vi.fn(),
    logout: vi.fn(),
  }
  return render(
    <AuthContext.Provider value={value}>
      <CandidatesPage applicationId={applicationId} />
    </AuthContext.Provider>,
  )
}

const page = (items: CompanyApplication[], total = items.length, n = 0) => json(200, { items, total, page: n, size: 25 })

describe('CandidatesPage', () => {
  it('lista a todas las personas sin mostrar datos de quien no los comparte ni la puntuación', async () => {
    routes({
      'GET /api/v1/companies/me/applications': () => page([hidden, jose]),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
    })
    renderPage()
    const list = await screen.findByRole('region', { name: 'Lista de candidatos' })
    expect(await within(list).findByRole('status')).toHaveTextContent('2 postulaciones')
    const rows = within(list).getAllByRole('link')
    expect(rows[0]).toHaveTextContent('Datos no compartidos')
    expect(rows[0]).toHaveTextContent('Analista de datos')
    expect(rows[1]).toHaveTextContent('José Pérez')
    expect(rows[1]).toHaveTextContent('En revisión')
    expect(rows[1]).toHaveTextContent('Análisis listo')
    expect(rows[1]).toHaveTextContent(/Entrevista:/)
    expect(rows[1]).toHaveAttribute('href', '/candidatos/app-1')
    expect(list).not.toHaveTextContent(/puntuaci|%/i)
    // Los borradores no se ofrecen como filtro.
    expect(within(screen.getByLabelText('Vacante')).queryByRole('option', { name: 'Borrador interno' })).not.toBeInTheDocument()
  })

  it('busca al dejar de escribir y filtra por vacante y etapa', async () => {
    routes({
      'GET /api/v1/companies/me/applications': (url) => page(url.searchParams.get('q') === 'nadie' ? [] : [jose]),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('José Pérez')

    await user.type(screen.getByLabelText('Nombre, correo o vacante'), 'jose')
    await waitFor(() => expect(searches().at(-1)!.searchParams.get('q')).toBe('jose'))
    expect(searches().filter((u) => u.searchParams.get('q') === 'j')).toHaveLength(0)

    await user.selectOptions(await screen.findByLabelText('Vacante'), 'vac-1')
    await waitFor(() => expect(searches().at(-1)!.searchParams.get('vacancyId')).toBe('vac-1'))
    await user.selectOptions(screen.getByLabelText('Etapa'), 'REVIEW')
    await waitFor(() => expect(searches().at(-1)!.searchParams.get('stage')).toBe('REVIEW'))

    await user.clear(screen.getByLabelText('Nombre, correo o vacante'))
    await user.type(screen.getByLabelText('Nombre, correo o vacante'), 'nadie')
    expect(await screen.findByText('Nadie coincide con estos filtros')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await waitFor(() => expect(searches().at(-1)!.searchParams.has('stage')).toBe(false))
    expect(screen.getByLabelText('Nombre, correo o vacante')).toHaveValue('')
  })

  it('carga más resultados por páginas', async () => {
    routes({
      'GET /api/v1/companies/me/applications': (url) =>
        url.searchParams.get('page') === '1' ? page([hidden], 26, 1) : page([jose], 26),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
    })
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Ver más (25 restantes)' }))
    expect(await screen.findByText('Datos no compartidos')).toBeInTheDocument()
    expect(searches().at(-1)!.searchParams.get('page')).toBe('1')
  })

  it('el detalle reúne CV, mensajes, entrevista y etapa', async () => {
    routes({
      'GET /api/v1/companies/me/applications': () => page([jose]),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
      'GET /api/v1/companies/me/applications/app-1': () => json(200, jose),
      'GET /api/v1/companies/me/vacancies/vac-1/applications/app-1/cv': () =>
        json(200, { url: 'https://almacen.ejemplo.test/cv-1.pdf', expiresAt: '2026-11-20T10:05:00Z' }),
    })
    const tab = { opener: {}, location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    const user = userEvent.setup()
    renderPage('app-1')

    const detail = await screen.findByRole('article', { name: 'José Pérez' })
    expect(detail).toHaveTextContent('jose@ejemplo.test')
    expect(within(detail).getByRole('link', { name: 'Backend Java' })).toHaveAttribute('href', '/vacantes/vac-1')
    expect(within(screen.getByRole('region', { name: 'Lista de candidatos' })).getByRole('link', { name: /José Pérez/ }))
      .toHaveAttribute('aria-current', 'page')

    await user.click(within(detail).getByRole('button', { name: 'Ver el CV de José Pérez (se abre en otra pestaña)' }))
    await waitFor(() => expect(tab.location.href).toBe('https://almacen.ejemplo.test/cv-1.pdf'))
    expect(tab.opener).toBeNull()

    await user.click(within(detail).getByRole('button', { name: 'Programar entrevista' }))
    expect(window.location.pathname + window.location.search).toBe('/entrevistas?postulacion=app-1')
    expect(within(detail).getByRole('button', { name: 'Seguimiento de José Pérez' })).toBeInTheDocument()
  })

  it('sin permiso de compartir, el detalle lo explica y no ofrece acciones', async () => {
    routes({
      'GET /api/v1/companies/me/applications': () => page([hidden]),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
      'GET /api/v1/companies/me/applications/app-2': () => json(200, hidden),
    })
    renderPage('app-2')
    const detail = await screen.findByRole('article', { name: 'Datos no compartidos' })
    expect(detail).toHaveTextContent('retiró el permiso de compartir sus datos')
    expect(within(detail).queryByRole('button')).not.toBeInTheDocument()
  })

  it('una postulación que ya no existe lo dice con claridad', async () => {
    routes({
      'GET /api/v1/companies/me/applications': () => page([]),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
      'GET /api/v1/companies/me/applications/app-9': () =>
        json(404, { code: 'application.not-found', detail: 'La postulación no existe' }),
    })
    renderPage('app-9')
    expect(await screen.findByText('Esta postulación no existe')).toBeInTheDocument()
  })
})

describe('Ruta /candidatos', () => {
  it('la empresa la abre desde el menú', async () => {
    window.history.replaceState(null, '', '/candidatos')
    const tokens = {
      accessToken: 'jwt-sintetico', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { id: 'u-1', email: 'admin@ejemplo.test', role: 'RECRUITER', companyId: 'c-1', createdAt: '' },
    }
    routes({
      'POST /api/v1/auth/refresh': () => json(200, tokens),
      'GET /api/v1/companies/me/applications': () => page([jose]),
      'GET /api/v1/companies/me/vacancies': () => json(200, vacancies),
    })
    render(<App />)
    expect(await screen.findByRole('heading', { level: 1, name: 'Candidatos' })).toBeInTheDocument()
    const nav = screen.getAllByRole('navigation', { name: 'Principal' })[0]!
    expect(within(nav).getByRole('link', { name: 'Candidatos' })).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByText('José Pérez')).toBeInTheDocument()
  })
})
