import { act, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RealtimeContext, type AppNotice, type NoticeListener, type RealtimeValue } from '@/lib/realtime-context'
import { applicationsApi } from './api'
import { ApplicationUpdatesContext, useApplicationUpdates } from './application-updates-context'
import { ApplicationUpdateNotice } from './application-update-notice'

afterEach(() => vi.restoreAllMocks())

function Harness({ enabled = true }: { enabled?: boolean }) {
  const value = useApplicationUpdates('token-sintetico', enabled)
  return <ApplicationUpdatesContext.Provider value={value}><ApplicationUpdateNotice /></ApplicationUpdatesContext.Provider>
}

function live() {
  const listeners = new Set<NoticeListener>()
  const value: RealtimeValue = {
    status: 'connecting',
    listen: (listener) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    emit: (notice) => listeners.forEach((listener) => listener(notice)),
  }
  return { value, send: (notice: AppNotice) => act(() => value.emit(notice)) }
}

describe('avisos de cambios de etapa', () => {
  it('recupera pendientes al entrar y no suma avisos duplicados sobre el contador del servidor', async () => {
    const load = vi.spyOn(applicationsApi, 'updates').mockResolvedValue({ unread: 2, updates: [] })
    const realtime = live()
    render(<RealtimeContext.Provider value={realtime.value}><Harness /></RealtimeContext.Provider>)
    expect(await screen.findByText('Tienes 2 cambios de etapa sin leer.')).toBeInTheDocument()
    realtime.send({ type: 'application.stage.changed', applicationId: 'a-1', stageChangeId: 2 })
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2))
    realtime.send({ type: 'application.stage.changed', applicationId: 'a-1', stageChangeId: 2 })
    await waitFor(() => expect(load).toHaveBeenCalledTimes(3))
    expect(screen.getByText('Tienes 2 cambios de etapa sin leer.')).toBeInTheDocument()
    load.mockResolvedValue({ unread: 0, updates: [] })
    realtime.send({ type: 'application.progress.read', applicationId: 'a-1' })
    await waitFor(() => expect(screen.queryByText('Tienes 2 cambios de etapa sin leer.')).not.toBeInTheDocument())
  })

  it('recupera avisos perdidos al reconectarse y volver a enfocar la ventana', async () => {
    const load = vi.spyOn(applicationsApi, 'updates').mockResolvedValueOnce({ unread: 0, updates: [] })
      .mockResolvedValue({ unread: 1, updates: [] })
    const realtime = live()
    render(<RealtimeContext.Provider value={realtime.value}><Harness /></RealtimeContext.Provider>)
    await waitFor(() => expect(load).toHaveBeenCalledOnce())
    realtime.send({ type: 'reconnected' })
    expect(await screen.findByText('Tienes 1 cambio de etapa sin leer.')).toBeInTheDocument()
    act(() => window.dispatchEvent(new Event('focus')))
    await waitFor(() => expect(load).toHaveBeenCalledTimes(3))
  })

  it('recupera un cambio ocurrido antes de que termine de abrirse el WebSocket', async () => {
    const load = vi.spyOn(applicationsApi, 'updates').mockResolvedValueOnce({ unread: 0, updates: [] })
      .mockResolvedValue({ unread: 1, updates: [] })
    const realtime = live()
    const view = render(<RealtimeContext.Provider value={realtime.value}><Harness /></RealtimeContext.Provider>)
    await waitFor(() => expect(load).toHaveBeenCalledOnce())
    view.rerender(<RealtimeContext.Provider value={{ ...realtime.value, status: 'online' }}><Harness /></RealtimeContext.Provider>)
    expect(await screen.findByText('Tienes 1 cambio de etapa sin leer.')).toBeInTheDocument()
  })

  it('no consulta avisos de candidato cuando la función está deshabilitada', async () => {
    const load = vi.spyOn(applicationsApi, 'updates')
    render(<Harness enabled={false} />)
    await act(async () => {})
    expect(load).not.toHaveBeenCalled()
    expect(screen.queryByText(/sin leer/)).not.toBeInTheDocument()
  })

  it('muestra un error recuperable si no puede consultar pendientes', async () => {
    vi.spyOn(applicationsApi, 'updates').mockRejectedValue(new Error('sin conexión'))
    render(<Harness />)
    expect(await screen.findByRole('button', { name: 'Volver a consultar avisos' })).toBeInTheDocument()
  })
})
