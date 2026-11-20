import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { navigate } from '@/lib/router'
import { publicVacancies, type PublicVacancySummary } from './api'
import { PublicVacanciesPage, PublicVacancyPage } from './public-vacancy-pages'

const offer: PublicVacancySummary = { id: 'v-1', title: 'Desarrollador/a backend Java', companyName: 'Equipo sintético', location: 'Quito', modality: 'REMOTO', publishedAt: '2026-11-18T15:00:00Z' }
const offers: PublicVacancySummary[] = [offer, { ...offer, id: 'v-2', title: 'Analista de datos', companyName: 'Empresa de ejemplo', location: 'Bogotá', modality: 'HIBRIDO', publishedAt: '2026-11-17T15:00:00Z' },
  { ...offer, id: 'v-3', title: 'Diseño de producto', location: 'Quito', modality: 'PRESENCIAL', publishedAt: '2026-11-16T15:00:00Z' }]
beforeEach(() => { window.history.replaceState(null, '', '/vacantes'); vi.spyOn(publicVacancies, 'list').mockResolvedValue(offers) })
afterEach(() => vi.restoreAllMocks())

describe('explorador de vacantes', () => {
  it('combina búsqueda sin acentos, ubicación y modalidad sin pedir datos otra vez', async () => {
    render(<PublicVacanciesPage />)
    await screen.findByRole('list', { name: 'Vacantes abiertas' })
    await userEvent.type(screen.getByRole('searchbox', { name: 'Puesto o empresa' }), 'sintetico')
    expect(screen.getByText('2 resultados de 3 vacantes cargadas')).toBeInTheDocument()
    await userEvent.type(screen.getByRole('searchbox', { name: 'Ubicación' }), 'quito')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Modalidad' }), 'REMOTO')
    expect(screen.getByText('1 resultado de 3 vacantes cargadas')).toBeInTheDocument()
    expect(screen.getByText(offer.title)).toBeInTheDocument()
    expect(screen.queryByText('Diseño de producto')).not.toBeInTheDocument()
    expect(publicVacancies.list).toHaveBeenCalledOnce()
    expect(new URLSearchParams(window.location.search).get('modalidad')).toBe('REMOTO')
  })
  it('distingue una búsqueda sin resultados y permite limpiar los filtros', async () => {
    render(<PublicVacanciesPage />)
    await screen.findByText(offer.title)
    await userEvent.type(screen.getByRole('searchbox', { name: 'Puesto o empresa' }), 'no-existe')
    expect(screen.getByText('No encontramos vacantes con esos filtros')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Ver todas las vacantes' }))
    expect(screen.getByText(offer.title)).toBeInTheDocument()
    expect(window.location.search).toBe('')
  })
  it('ordena las fechas y títulos con los datos publicados', async () => {
    render(<PublicVacanciesPage />)
    const list = await screen.findByRole('list', { name: 'Vacantes abiertas' })
    const titles = () => within(list).getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(titles()[0]).toBe(offer.title)
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Ordenar por' }), 'antiguas')
    expect(titles()[0]).toBe('Diseño de producto')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Ordenar por' }), 'titulo')
    expect(titles()[0]).toBe('Analista de datos')
  })
  it('pagina de doce en doce y cambia de filtro desde la primera página', async () => {
    vi.mocked(publicVacancies.list).mockResolvedValue(Array.from({ length: 13 }, (_, i) => ({ ...offer, id: `v-${String(i).padStart(2, '0')}`, title: `Puesto ${i}` })))
    render(<PublicVacanciesPage />)
    const list = await screen.findByRole('list', { name: 'Vacantes abiertas' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(12)
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument()
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Modalidad' }), 'REMOTO')
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
    expect(new URLSearchParams(window.location.search).has('pagina')).toBe(false)
  })
  it('restaura los filtros desde la URL e ignora valores inválidos de página', async () => {
    window.history.replaceState(null, '', '/vacantes?q=datos&ubicacion=bogota&modalidad=HIBRIDO&pagina=999')
    render(<PublicVacanciesPage />)
    expect(await screen.findByText('Analista de datos')).toBeInTheDocument()
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Puesto o empresa' })).toHaveValue('datos')
    act(() => navigate('/vacantes?pagina=NaN&modalidad=inventada'))
    expect(screen.getByText('3 resultados de 3 vacantes cargadas')).toBeInTheDocument()
  })
  it('incluye una vuelta a los resultados y descarta destinos externos', async () => {
    window.history.replaceState(null, '', '/vacantes?q=backend')
    const page = render(<PublicVacanciesPage />)
    const link = await screen.findByRole('link', { name: `Ver vacante: ${offer.title} en ${offer.companyName}` })
    const detailUrl = new URL(link.getAttribute('href')!, window.location.origin)
    expect(detailUrl.searchParams.get('volver')).toBe('/vacantes?q=backend')
    page.unmount()
    vi.spyOn(publicVacancies, 'get').mockResolvedValue({ ...offer, description: 'Descripción sintética.', criteria: [] })
    window.history.replaceState(null, '', '/vacantes/v-1?volver=https://externo.test')
    const detail = render(<PublicVacancyPage id="v-1" signedIn={false} />)
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(offer.title)
    expect(screen.getByRole('link', { name: 'Vacantes abiertas' })).toHaveAttribute('href', '/vacantes')
    detail.unmount()
    window.history.replaceState(null, '', detailUrl.pathname + detailUrl.search)
    render(<PublicVacancyPage id="v-1" signedIn={false} />)
    expect(await screen.findByRole('link', { name: 'Vacantes abiertas' })).toHaveAttribute('href', '/vacantes?q=backend')
  })
  it('explica el límite real de las vacantes cargadas', async () => {
    vi.mocked(publicVacancies.list).mockResolvedValue(Array.from({ length: 100 }, (_, i) => ({ ...offer, id: `v-${i}` })))
    render(<PublicVacanciesPage />)
    expect(await screen.findByRole('note')).toHaveTextContent('hasta 100 vacantes recientes')
  })
  it('si no existen vacantes no simula resultados y permite recuperar un error de carga', async () => {
    vi.mocked(publicVacancies.list).mockRejectedValueOnce(new Error('fallo sintético')).mockResolvedValue([])
    render(<PublicVacanciesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('No hay vacantes abiertas ahora')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Vacantes abiertas' })).not.toBeInTheDocument()
  })
})
