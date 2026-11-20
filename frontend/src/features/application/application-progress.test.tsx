import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/features/auth/api'
import { applicationsApi, type ApplicationProgress } from './api'
import { ApplicationProgressPanel } from './application-progress'

afterEach(() => vi.restoreAllMocks())
beforeEach(() => vi.spyOn(applicationsApi, 'markProgressRead').mockResolvedValue(undefined))

const received: ApplicationProgress = {
  stage: 'RECEIVED', history: [{ id: 1, fromStage: null, stage: 'RECEIVED', message: null, changedAt: '2026-11-16T10:00:00Z' }],
}
const reviewed: ApplicationProgress = {
  stage: 'REVIEW', history: [...received.history, {
    id: 2, fromStage: 'RECEIVED', stage: 'REVIEW', message: 'Revisaremos tu experiencia.', changedAt: '2026-11-16T11:00:00Z',
  }],
}

describe('seguimiento de postulaciones', () => {
  it('muestra el historial al candidato sin controles para cambiarlo', async () => {
    const load = vi.spyOn(applicationsApi, 'progress').mockResolvedValue(reviewed)
    render(<ApplicationProgressPanel token="sintetico" applicationId="a-1" label="Backend" />)
    expect(load).not.toHaveBeenCalled()
    expect(applicationsApi.markProgressRead).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Seguimiento de Backend' }))
    expect(await screen.findByText('Etapa actual: En revisión')).toBeInTheDocument()
    expect(screen.getByText('Revisaremos tu experiencia.')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(load).toHaveBeenCalledWith('sintetico', 'a-1', undefined)
    await waitFor(() => expect(applicationsApi.markProgressRead).toHaveBeenCalledWith('sintetico', 'a-1', 2))
  })

  it('guarda una transición válida y comunica que el mensaje es público', async () => {
    vi.spyOn(applicationsApi, 'progress').mockResolvedValue(received)
    const move = vi.spyOn(applicationsApi, 'moveStage').mockResolvedValue(reviewed)
    const refresh = vi.fn()
    render(<ApplicationProgressPanel token="sintetico" applicationId="a-1" vacancyId="v-1" label="Ana" onStageChanged={refresh} />)
    await userEvent.click(screen.getByRole('button', { name: 'Seguimiento de Ana' }))
    const select = await screen.findByRole('combobox', { name: 'Nueva etapa' })
    expect(screen.queryByRole('option', { name: 'Contratada' })).not.toBeInTheDocument()
    expect(screen.getByText(/Este mensaje será visible para el candidato/)).toBeInTheDocument()
    await userEvent.selectOptions(select, 'REVIEW')
    await userEvent.type(screen.getByLabelText('Mensaje para el candidato (opcional)'), 'Revisaremos tu experiencia.')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar etapa' }))
    expect(await screen.findByText('Etapa actual: En revisión')).toBeInTheDocument()
    expect(move).toHaveBeenCalledExactlyOnceWith('sintetico', 'v-1', 'a-1', 'RECEIVED', 'REVIEW', 'Revisaremos tu experiencia.')
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('recarga un conflicto sin repetir el cambio automáticamente', async () => {
    vi.spyOn(applicationsApi, 'progress').mockResolvedValueOnce(received).mockResolvedValue(reviewed)
    const move = vi.spyOn(applicationsApi, 'moveStage').mockRejectedValue(new ApiError(409, 'application.stage.conflict', 'Otra persona cambió la etapa'))
    render(<ApplicationProgressPanel token="sintetico" applicationId="a-1" vacancyId="v-1" label="Ana" />)
    await userEvent.click(screen.getByRole('button', { name: 'Seguimiento de Ana' }))
    await userEvent.selectOptions(await screen.findByRole('combobox'), 'REVIEW')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar etapa' }))
    expect(await screen.findByText('Otra persona cambió la etapa.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Etapa actual: En revisión')).toBeInTheDocument())
    expect(move).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Guardar etapa' })).toBeDisabled()
  })

  it('muestra un proceso finalizado sin permitir nuevas etapas', async () => {
    vi.spyOn(applicationsApi, 'progress').mockResolvedValue({ stage: 'REJECTED', history: [] })
    render(<ApplicationProgressPanel token="sintetico" applicationId="a-1" vacancyId="v-1" label="Ana" />)
    await userEvent.click(screen.getByRole('button', { name: 'Seguimiento de Ana' }))
    expect(await screen.findByText('Etapa actual: No continúa')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('no marca como leído desde una pestaña oculta', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    vi.spyOn(applicationsApi, 'progress').mockResolvedValue(reviewed)
    render(<ApplicationProgressPanel token="sintetico" applicationId="a-1" label="Backend" />)
    await userEvent.click(screen.getByRole('button', { name: 'Seguimiento de Backend' }))
    await screen.findByText('Etapa actual: En revisión')
    expect(applicationsApi.markProgressRead).not.toHaveBeenCalled()
    visibility.mockReturnValue('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await waitFor(() => expect(applicationsApi.markProgressRead).toHaveBeenCalledWith('sintetico', 'a-1', 2))
  })

  it('permite reintentar una marca de lectura fallida sin ocultar el historial', async () => {
    vi.spyOn(applicationsApi, 'progress').mockResolvedValue(reviewed)
    vi.mocked(applicationsApi.markProgressRead).mockRejectedValueOnce(new Error('sin conexión'))
    render(<ApplicationProgressPanel token="sintetico" applicationId="a-1" label="Backend" />)
    await userEvent.click(screen.getByRole('button', { name: 'Seguimiento de Backend' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar marcar como leído' }))
    await waitFor(() => expect(applicationsApi.markProgressRead).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Revisaremos tu experiencia.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Reintentar marcar como leído' })).not.toBeInTheDocument())
  })
})
