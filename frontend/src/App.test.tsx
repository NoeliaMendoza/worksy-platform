import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
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
  it('carga el ranking del mock dentro de la estructura con navegación', async () => {
    render(<App />)
    expect(screen.getAllByRole('navigation', { name: 'Principal' }).length).toBeGreaterThan(0)
    expect(await screen.findByRole('heading', { level: 1 }, { timeout: 2000 })).toHaveTextContent('Java')
  })
})
