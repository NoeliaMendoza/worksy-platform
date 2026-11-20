import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { ScreenBoundary } from './screen-boundary'

afterEach(() => vi.restoreAllMocks())

it('contiene una pantalla fallida, conserva la navegación y ofrece recuperación explícita', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  const retry = vi.fn()
  function UnavailableScreen(): never { throw new Error('fallo sintético de carga') }
  render(<><nav aria-label="Principal"><a href="/postulaciones">Mis postulaciones</a></nav>
    <ScreenBoundary onRetry={retry}><UnavailableScreen /></ScreenBoundary></>)
  expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cargar esta pantalla')
  expect(screen.getByRole('link', { name: 'Mis postulaciones' })).toHaveAttribute('href', '/postulaciones')
  expect(screen.queryByText('fallo sintético de carga')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Recargar aplicación' }))
  expect(retry).toHaveBeenCalledOnce()
})
