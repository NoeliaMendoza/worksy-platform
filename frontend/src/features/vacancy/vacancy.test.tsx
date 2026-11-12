import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '@/App'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import type { CompanyVacancy } from './api'
import { CompanyVacanciesPage } from './company-vacancies-page'
import { CompanyVacancyPage, NewVacancyPage, VacancyRankingPage } from './company-vacancy-pages'

// Datos sintéticos: empresas y dominios ficticios.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  window.history.replaceState(null, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function routes(handlers: Record<string, (init?: RequestInit) => Response>) {
  fetchMock.mockImplementation((url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`
    const match = Object.entries(handlers).find(([k]) => key === k || key.endsWith(` ${k}`) || key.endsWith(k))
    return Promise.resolve(match ? match[1](init) : new Response(null, { status: 404 }))
  })
}

const calls = (key: string) =>
  fetchMock.mock.calls.filter(([url, init]) => `${(init as RequestInit | undefined)?.method ?? 'GET'} ${url}` === key) as [string, RequestInit][]

function withRecruiter(children: ReactNode) {
  const value: AuthContextValue = {
    state: {
      status: 'authenticated',
      accessToken: 'jwt-sintetico',
      expiresAt: Date.now() + 900_000,
      user: { id: 'u-1', email: 'recluta@andina.test', role: 'RECRUITER', companyId: 'c-1', createdAt: '' },
    },
    login: vi.fn(),
    logout: vi.fn(),
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

const vacancy = (patch: Partial<CompanyVacancy> = {}): CompanyVacancy => ({
  id: 'v-1',
  title: 'Desarrollador/a backend Java',
  description: 'Equipo de pagos con Spring Boot y PostgreSQL.',
  location: 'Lima',
  modality: 'HIBRIDO',
  status: 'DRAFT',
  criteria: [{ key: 'q1', label: 'Spring Boot', question: '¿Tiene experiencia con Spring Boot?', kind: 'YES_NO', options: ['Sí', 'No'] }],
  createdAt: '2026-11-10T10:00:00Z',
  updatedAt: '2026-11-10T10:00:00Z',
  publishedAt: null,
  closedAt: null,
  ...patch,
})

describe('CompanyVacanciesPage', () => {
  it('sin vacantes invita a crear la primera', async () => {
    routes({ 'GET /api/v1/companies/me/vacancies': () => json(200, []) })
    render(withRecruiter(<CompanyVacanciesPage />))
    expect(await screen.findByText('Todavía no tienes vacantes')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear vacante' })).toBeInTheDocument()
  })

  it('lista las vacantes con su estado en palabras', async () => {
    routes({
      'GET /api/v1/companies/me/vacancies': () =>
        json(200, [vacancy(), vacancy({ id: 'v-2', title: 'Analista de datos', status: 'PUBLISHED', publishedAt: '2026-11-10T11:00:00Z' })]),
    })
    render(withRecruiter(<CompanyVacanciesPage />))
    const list = await screen.findByRole('list', { name: 'Vacantes de la empresa' })
    const rows = within(list).getAllByRole('link')
    expect(rows[0]).toHaveTextContent('Borrador')
    expect(rows[1]).toHaveTextContent('Publicada')
    expect(rows[1]).toHaveAttribute('href', '/vacantes/v-2')
  })
})

describe('Editor de vacante', () => {
  it('crea la vacante con un criterio de varias opciones y la publica', async () => {
    routes({
      'POST /api/v1/companies/me/vacancies': () => json(201, vacancy()),
      'POST /api/v1/companies/me/vacancies/v-1/publish': () => json(200, vacancy({ status: 'PUBLISHED' })),
    })
    const user = userEvent.setup()
    render(withRecruiter(<NewVacancyPage />))

    await user.type(screen.getByLabelText('Título'), 'Desarrollador/a backend Java')
    await user.type(screen.getByLabelText('Ubicación'), 'Lima')
    await user.click(screen.getByRole('radio', { name: 'Remoto' }))
    await user.type(screen.getByLabelText('Descripción'), 'Equipo de pagos con Spring Boot y PostgreSQL.')

    const first = screen.getByRole('article', { name: 'Criterio 1' })
    await user.type(within(first).getByLabelText('Nombre corto'), 'Años en Java')
    await user.type(within(first).getByLabelText('Pregunta'), '¿Cuántos años de experiencia tiene con Java?')
    await user.click(within(first).getByRole('radio', { name: 'Varias opciones' }))
    await user.type(within(first).getByLabelText('Opción 1 del criterio 1'), 'Más de 3')
    await user.type(within(first).getByLabelText('Opción 2 del criterio 1'), 'Menos de 3')

    await user.click(screen.getByRole('button', { name: 'Guardar y publicar' }))

    await waitFor(() => expect(calls('POST /api/v1/companies/me/vacancies/v-1/publish')).toHaveLength(1))
    expect(JSON.parse(calls('POST /api/v1/companies/me/vacancies')[0]![1].body as string)).toEqual({
      title: 'Desarrollador/a backend Java',
      location: 'Lima',
      modality: 'REMOTO',
      description: 'Equipo de pagos con Spring Boot y PostgreSQL.',
      criteria: [
        { label: 'Años en Java', question: '¿Cuántos años de experiencia tiene con Java?', kind: 'CHOICE', options: ['Más de 3', 'Menos de 3'] },
      ],
    })
    expect(window.location.pathname).toBe('/vacantes/v-1')
  }, 20_000)

  it('valida en el cliente y muestra el mensaje de la API', async () => {
    routes({
      'POST /api/v1/companies/me/vacancies': () =>
        json(400, { code: 'vacancy.criterion.invalid', detail: 'La etiqueta del criterio debe tener entre 2 y 60 caracteres' }),
    })
    const user = userEvent.setup()
    render(withRecruiter(<NewVacancyPage />))

    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(screen.getByLabelText('Título')).toHaveAccessibleDescription(/al menos 3 caracteres/)
    expect(fetchMock).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText('Título'), 'Analista')
    await user.type(screen.getByLabelText('Ubicación'), 'Lima')
    await user.type(screen.getByLabelText('Descripción'), 'Descripción suficientemente larga del puesto.')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La etiqueta del criterio debe tener entre 2 y 60 caracteres.')
  })

  it('agrega, reordena y quita criterios', async () => {
    const user = userEvent.setup()
    render(withRecruiter(<NewVacancyPage />))
    await user.type(within(screen.getByRole('article', { name: 'Criterio 1' })).getByLabelText('Nombre corto'), 'Primero')
    await user.click(screen.getByRole('button', { name: 'Agregar criterio' }))
    await user.click(screen.getByRole('button', { name: 'Subir criterio 2' }))
    expect(within(screen.getByRole('article', { name: 'Criterio 2' })).getByLabelText('Nombre corto')).toHaveValue('Primero')
    await user.click(screen.getByRole('button', { name: 'Quitar criterio 1' }))
    expect(screen.getAllByRole('article')).toHaveLength(1)
  })
})

describe('Vacante publicada', () => {
  it('se cierra solo tras confirmar', async () => {
    const published = vacancy({ status: 'PUBLISHED', publishedAt: '2026-11-10T11:00:00Z' })
    routes({
      'GET /api/v1/companies/me/vacancies/v-1': () => json(200, published),
      'POST /api/v1/companies/me/vacancies/v-1/close': () =>
        json(200, { ...published, status: 'CLOSED', closedAt: '2026-11-10T12:00:00Z' }),
    })
    const user = userEvent.setup()
    render(withRecruiter(<CompanyVacancyPage id="v-1" />))

    await user.click(await screen.findByRole('button', { name: 'Cerrar vacante' }))
    expect(screen.getByText('¿Cerrar esta vacante?')).toBeInTheDocument()
    expect(calls('POST /api/v1/companies/me/vacancies/v-1/close')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Sí, cerrar vacante' }))
    expect(await screen.findByText('Cerrada', { selector: 'span' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cerrar vacante' })).not.toBeInTheDocument()
  })

  it('el ranking de una vacante real muestra el estado vacío honesto', async () => {
    routes({
      'GET /api/v1/companies/me/vacancies/v-1/ranking': () => json(200, { candidates: [], received: 0, pending: 0, failed: 0, notRequested: 0 }),
      'GET /api/v1/companies/me/vacancies/v-1': () => json(200, vacancy({ status: 'PUBLISHED', publishedAt: '2026-11-10T11:00:00Z' })),
      'GET /api/v1/companies/me': () => json(200, { id: 'c-1', name: 'Andina Cargo', createdAt: '' }),
      'GET /api/v1/companies/me/vacancies/v-1/applications': () => json(200, []),
    })
    render(withRecruiter(<VacancyRankingPage id="v-1" />))
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Desarrollador/a backend Java')
    expect(screen.getByText('Todavía no hay CV para esta vacante')).toBeInTheDocument()
    expect(screen.getByText('Andina Cargo')).toBeInTheDocument()
    expect(screen.queryByText('Valeria Quispe Romero')).not.toBeInTheDocument()
  })

  it('con postulaciones sin preselección lo dice y lleva a verlas', async () => {
    routes({
      'GET /api/v1/companies/me/vacancies/v-1/ranking': () => json(200, { candidates: [], received: 1, pending: 0, failed: 0, notRequested: 1 }),
      'GET /api/v1/companies/me/vacancies/v-1': () => json(200, vacancy({ status: 'PUBLISHED', publishedAt: '2026-11-10T11:00:00Z' })),
      'GET /api/v1/companies/me': () => json(200, { id: 'c-1', name: 'Andina Cargo', createdAt: '' }),
      'GET /api/v1/companies/me/vacancies/v-1/applications': () =>
        json(200, [{ id: 'a-1', appliedAt: '2026-11-11T10:00:00Z', shared: true, candidateEmail: 'ana@ejemplo.test', cvId: 'cv-1' }]),
    })
    render(withRecruiter(<VacancyRankingPage id="v-1" />))
    expect(await screen.findByText('1 postulación espera la preselección')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver postulaciones' })).toBeInTheDocument()
  })
})

describe('Ranking conectado', () => {
  const candidate = {
    applicationId: 'a-1', email: 'ana@ejemplo.test', fullName: null, headline: null, location: null,
    appliedAt: '2026-11-11T10:00:00Z', status: 'NEEDS_REVIEW', modelVersion: 'baseline-bow-1',
    overallScore: 0.75, confidence: 0.55,
    answers: [{ criterionId: 'q1', answer: 'Sí', score: 1, probability: 0.55 }], decision: null,
  }

  function rankingRoutes(saveFails = false) {
    let decision: string | null = null
    routes({
      'GET /api/v1/companies/me/vacancies/v-1': () => json(200, vacancy({ status: 'PUBLISHED' })),
      'GET /api/v1/companies/me': () => json(200, { name: 'Andina Cargo' }),
      'GET /api/v1/companies/me/vacancies/v-1/ranking': () =>
        json(200, { candidates: [{ ...candidate, decision }], received: 1, pending: 0, failed: 0, notRequested: 0 }),
      'PUT /api/v1/companies/me/vacancies/v-1/ranking/a-1/decision': (init) => {
        if (saveFails) return json(503, { detail: 'No se pudo guardar la decisión.' })
        decision = JSON.parse(init?.body as string).decision
        return new Response(null, { status: 204 })
      },
    })
  }

  it('actualiza al terminar el análisis y deja de consultar cuando no hay pendientes', async () => {
    let requests = 0
    routes({
      'GET /api/v1/companies/me/vacancies/v-1': () => json(200, vacancy({ status: 'PUBLISHED' })),
      'GET /api/v1/companies/me': () => json(200, { name: 'Andina Cargo' }),
      'GET /api/v1/companies/me/vacancies/v-1/ranking': () => {
        requests++
        return json(200, { candidates: requests > 1 ? [candidate] : [], received: 1,
          pending: requests > 1 ? 0 : 1, failed: 0, notRequested: 0 })
      },
    })
    vi.useFakeTimers()
    try {
      await act(async () => { render(withRecruiter(<VacancyRankingPage id="v-1" />)) })
      expect(screen.getByText('Analizando los CV')).toBeInTheDocument()
      await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
      expect(screen.getByRole('heading', { name: candidate.email })).toBeInTheDocument()
      expect(requests).toBe(2)
      await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
      expect(requests).toBe(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('muestra respuestas reales y conserva la decisión al volver a entrar', async () => {
    rankingRoutes()
    const user = userEvent.setup()
    const page = render(withRecruiter(<VacancyRankingPage id="v-1" />))
    expect(await screen.findByRole('heading', { name: candidate.email })).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: `Compatibilidad de ${candidate.email}` })).toHaveAttribute('aria-valuenow', '75')
    expect(screen.getByText('Análisis con dudas: revisa el CV')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver evidencia' }))
    expect(screen.getByText(/El análisis no incluye una cita del PDF/)).toBeInTheDocument()
    expect(screen.queryByText('El CV no menciona este criterio.')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Preseleccionar' }))
    expect(await screen.findByText('Decisión guardada.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Preseleccionar' })).toHaveAttribute('aria-pressed', 'true')
    page.unmount()
    render(withRecruiter(<VacancyRankingPage id="v-1" />))
    expect(await screen.findByRole('button', { name: 'Preseleccionar' })).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: 'Preseleccionar' }))
    await screen.findByText('Decisión guardada.')
    expect(screen.getByRole('button', { name: 'Preseleccionar' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('si falla el guardado no cambia la decisión ni anuncia éxito', async () => {
    rankingRoutes(true)
    const user = userEvent.setup()
    render(withRecruiter(<VacancyRankingPage id="v-1" />))
    await user.click(await screen.findByRole('button', { name: 'Descartar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El servicio no está disponible ahora.')
    expect(screen.getByRole('button', { name: 'Descartar' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByText('Decisión guardada.')).not.toBeInTheDocument()
  })
})

describe('Vacantes públicas sin sesión', () => {
  it('lista y detalle con las preguntas que se evaluarán', async () => {
    const summary = { id: 'v-9', title: 'Analista de datos', companyName: 'Pacífico Retail', location: 'Remoto', modality: 'REMOTO', publishedAt: '2026-11-10T11:00:00Z' }
    routes({
      'POST /api/v1/auth/refresh': () => json(401, { code: 'auth.refresh.invalid' }),
      'GET /api/v1/vacancies': () => json(200, [summary]),
      'GET /api/v1/vacancies/v-9': () =>
        json(200, { ...summary, description: 'Análisis de ventas.', criteria: [{ key: 'q1', label: 'SQL', question: '¿Usa SQL a diario?', kind: 'YES_NO', options: ['Sí', 'No'] }] }),
    })
    window.history.replaceState(null, '', '/vacantes')
    const user = userEvent.setup()
    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Vacantes abiertas' })).toBeInTheDocument()
    await user.click(await screen.findByRole('link', { name: /Analista de datos/ }))
    expect(await screen.findByText('¿Usa SQL a diario?')).toBeInTheDocument()
    expect(screen.getByText('Qué se evaluará en tu CV')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })
})
