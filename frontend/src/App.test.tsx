import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'
import { RankingView } from '@/features/ranking/ranking-view'
import { vacancyRankingMock } from '@/mocks/vacancy-ranking'

const ready = { status: 'ready', data: structuredClone(vacancyRankingMock) } as const

describe('RankingView (panel del reclutador)', () => {
  it('muestra la vacante, el avance y los CV en orden', () => {
    render(<RankingView state={ready} onRetry={() => {}} onDecide={() => {}} />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Desarrollador/a backend Java')
    expect(screen.getByText(/Ninguno avanza ni se descarta sin tu decisión/)).toBeInTheDocument()
    const list = screen.getByRole('list', { name: /orden de compatibilidad/ })
    const rows = within(list).getAllByRole('article')
    expect(rows).toHaveLength(8)
    expect(rows[0]).toHaveAccessibleName(/Valeria Quispe Romero/)
  })

  it('filtra por decisión y ofrece salida cuando el filtro queda vacío', async () => {
    const user = userEvent.setup()
    const data = structuredClone(vacancyRankingMock)
    data.candidates = data.candidates.map((c) => ({ ...c, decision: null }))
    render(<RankingView state={{ status: 'ready', data }} onRetry={() => {}} onDecide={() => {}} />)

    await user.click(screen.getByRole('button', { name: /^Descartados\s*,\s*0$/ }))
    expect(screen.getByText('No hay CV en «Descartados»')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver todos' }))
    expect(screen.getAllByRole('article')).toHaveLength(8)
  })

  it('anuncia la decisión a lectores de pantalla', async () => {
    const user = userEvent.setup()
    const onDecide = vi.fn()
    render(<RankingView state={ready} onRetry={() => {}} onDecide={onDecide} />)
    const row = screen.getByRole('article', { name: /Andrés Villanueva Soto/ })
    await user.click(within(row).getByRole('button', { name: 'Preseleccionar' }))
    expect(onDecide).toHaveBeenCalledWith('c-102', 'preseleccionado')
    expect(screen.getByRole('status')).toHaveTextContent('Andrés Villanueva Soto: preseleccionado.')
  })

  it('estado de carga', () => {
    render(<RankingView state={{ status: 'loading' }} onRetry={() => {}} onDecide={() => {}} />)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando el ranking')
  })

  it('estado de error con reintento', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    render(<RankingView state={{ status: 'error', message: 'Sin conexión.' }} onRetry={onRetry} onDecide={() => {}} />)
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cargar el ranking')
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('estado vacío', () => {
    const data = { ...vacancyRankingMock, candidates: [] }
    render(<RankingView state={{ status: 'ready', data }} onRetry={() => {}} onDecide={() => {}} />)
    expect(screen.getByText('Todavía no hay CV para esta vacante')).toBeInTheDocument()
  })
})

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    window.history.replaceState(null, '', '/')
  })

  function stubRefresh(response: Response) {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(response)))
  }

  it('sin sesión muestra el inicio de sesión', async () => {
    stubRefresh(new Response(JSON.stringify({ code: 'auth.refresh.invalid' }), { status: 401 }))
    render(<App />)
    expect(screen.getByRole('status')).toHaveTextContent('Comprobando tu sesión')
    expect(await screen.findByRole('heading', { level: 1, name: 'Inicia sesión' })).toBeInTheDocument()
  })

  it('con sesión carga el ranking dentro de la estructura y permite cerrar sesión', async () => {
    const tokens = {
      accessToken: 'jwt-sintetico',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      user: { id: 'u-1', email: 'reclutadora@ejemplo.test', role: 'RECRUITER', companyId: 'c-1', createdAt: '' },
    }
    stubRefresh(new Response(JSON.stringify(tokens), { status: 200 }))
    // El ranking con candidatos sintéticos vive en la vacante de ejemplo; en "/" está la lista real de vacantes.
    window.history.replaceState(null, '', '/vacantes/ejemplo')
    render(<App />)
    expect(await screen.findByRole('heading', { level: 1 }, { timeout: 2000 })).toHaveTextContent('Java')
    expect(screen.getByText('Ejemplo con datos sintéticos.')).toBeInTheDocument()
    expect(screen.getAllByRole('navigation', { name: 'Principal' }).length).toBeGreaterThan(0)
    expect(screen.getAllByText('reclutadora@ejemplo.test').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Reclutador/a').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: 'Cerrar sesión' }).length).toBeGreaterThan(0)
  })

  function sessionAs(role: string, email: string) {
    stubRefresh(
      new Response(
        JSON.stringify({
          accessToken: 'jwt-sintetico',
          tokenType: 'Bearer',
          expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
          user: { id: 'u-2', email, role, companyId: null, createdAt: '' },
        }),
        { status: 200 },
      ),
    )
  }

  it('una cuenta de candidato ve sus postulaciones y nunca el ranking de otras personas', async () => {
    sessionAs('CANDIDATE', 'candidata@ejemplo.test')
    render(<App />)
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Tus postulaciones')
    expect(screen.queryByText(/Ranking de/)).not.toBeInTheDocument()
    expect(screen.queryByText('Valeria Quispe Romero')).not.toBeInTheDocument()
    const nav = screen.getAllByRole('navigation', { name: 'Principal' })[0]!
    expect(within(nav).getByRole('link', { name: 'Mis postulaciones' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).queryByRole('link', { name: 'Candidatos' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Candidato/a').length).toBeGreaterThan(0)
  })

  it('la administración de Worksy ve su propio inicio', async () => {
    sessionAs('PLATFORM_ADMIN', 'admin@ejemplo.test')
    render(<App />)
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Administración de Worksy')
    expect(screen.queryByText(/Ranking de/)).not.toBeInTheDocument()
  })
})
