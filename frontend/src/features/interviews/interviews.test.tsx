import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { RealtimeContext, type NoticeListener, type RealtimeValue } from '@/lib/realtime-context'
import { interviewsApi, type Interview, type InterviewItem } from './api'
import { InterviewForm } from './interview-form'
import { InterviewsPage } from './interviews-page'
import { InterviewInboxContext, useInterviewInbox } from './interview-inbox-context'
import { InterviewNotice } from './interview-notice'
import { interviewTime, localDateTime, toInstant } from './interview-time'

vi.mock('@/features/auth/auth-context', () => ({ useAuth: vi.fn() }))
const invitation: Interview = { id: 'i-1', applicationId: 'a-1', version: 2, title: 'Entrevista técnica', startsAt: '2099-04-10T15:00:00Z',
  durationMinutes: 30, zoneId: 'America/Guayaquil', mode: 'ONLINE', place: 'https://example.test/sala', note: 'Preparar preguntas.', status: 'INVITED', updatedAt: '2099-04-01T12:00:00Z' }
const row: InterviewItem = { interview: invitation, companyName: 'Empresa sintética', vacancyTitle: 'Backend', candidateEmail: 'persona@ejemplo.test', unread: true }
function auth(company = false) {
  vi.mocked(useAuth).mockReturnValue({ state: { status: 'authenticated', accessToken: 'sintetico', expiresAt: 9999999999,
    user: { id: 'u-1', email: 'persona@ejemplo.test', role: company ? 'RECRUITER' : 'CANDIDATE', companyId: company ? 'c-1' : null, createdAt: '2026-01-01T00:00:00Z' } }, login: vi.fn(), logout: vi.fn() })
}
beforeEach(() => {
  window.history.replaceState(null, '', '/entrevistas')
  auth()
  vi.spyOn(interviewsApi, 'list').mockResolvedValue({ items: [row], hasMore: false })
  vi.spyOn(interviewsApi, 'history').mockResolvedValue([])
  vi.spyOn(interviewsApi, 'read').mockResolvedValue(undefined)
})
afterEach(() => vi.restoreAllMocks())

describe('entrevistas', () => {
  it('muestra zona explícita y marca solo la versión que se abrió', async () => {
    render(<InterviewsPage />)
    await screen.findByText('Entrevista técnica')
    expect(screen.queryByRole('button', { name: 'Actualizar' })).not.toBeInTheDocument()
    expect(interviewsApi.read).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Ver detalles y cambios sin leer' }))
    expect(screen.getByText('Preparar preguntas.')).toBeInTheDocument()
    expect(screen.getByText('Zona: America/Guayaquil')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Abrir reunión/ })).toHaveAttribute('rel', 'noopener noreferrer')
    await waitFor(() => expect(interviewsApi.read).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2))
  })
  it('no guarda lectura en pestaña oculta y la recupera al volver', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    render(<InterviewsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalles y cambios sin leer' }))
    expect(interviewsApi.read).not.toHaveBeenCalled()
    visibility.mockReturnValue('visible')
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    await waitFor(() => expect(interviewsApi.read).toHaveBeenCalledWith('sintetico', 'i-1', 2))
  })
  it('confirma con versión y no ofrece gestión empresarial al candidato', async () => {
    const respond = vi.spyOn(interviewsApi, 'respond').mockResolvedValue({ ...invitation, version: 3, status: 'CONFIRMED' })
    render(<InterviewsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalles y cambios sin leer' }))
    expect(screen.queryByRole('button', { name: 'Cancelar entrevista' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar asistencia' }))
    expect(await screen.findByText('Respuesta guardada.')).toBeInTheDocument()
    expect(respond).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2, 'CONFIRMED')
  })
  it('no repite una respuesta tras conflicto y exige revisar la lista', async () => {
    const respond = vi.spyOn(interviewsApi, 'respond').mockRejectedValue(new ApiError(409, 'interview.conflict', 'Cambió'))
    render(<InterviewsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalles y cambios sin leer' }))
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar invitación' }))
    expect(await screen.findByText(/La entrevista cambió/)).toBeInTheDocument()
    expect(respond).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Confirmar asistencia' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar consulta' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalles y cambios sin leer' }))
    expect(screen.getByRole('button', { name: 'Confirmar asistencia' })).toBeEnabled()
    expect(respond).toHaveBeenCalledOnce()
  })
  it('cancela desde la empresa y explica el alcance sobre salas externas', async () => {
    auth(true)
    const cancel = vi.spyOn(interviewsApi, 'cancel').mockResolvedValue({ ...invitation, version: 3, status: 'CANCELLED' })
    render(<InterviewsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalles y cambios sin leer' }))
    expect(screen.queryByRole('button', { name: 'Confirmar asistencia' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar entrevista' }))
    expect(await screen.findByText(/cancélala también en ese servicio/)).toBeInTheDocument()
    expect(cancel).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2)
  })
  it('valida el contexto de la postulación antes de permitir agendar', async () => {
    auth(true)
    window.history.replaceState(null, '', '/entrevistas?postulacion=ajena')
    vi.spyOn(interviewsApi, 'context').mockRejectedValue(new ApiError(404, 'interview.not-found', 'No existe'))
    render(<InterviewsPage />)
    expect(await screen.findByText(/No existe/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar invitación' })).not.toBeInTheDocument()
  })
  it('reutiliza el identificador y los datos exactos tras perder la respuesta de creación', async () => {
    const schedule = vi.spyOn(interviewsApi, 'schedule').mockRejectedValueOnce(new ApiError(0, 'network', 'Red')).mockResolvedValue(invitation)
    const saved = vi.fn()
    render(<InterviewForm token="sintetico" applicationId="a-1" onSaved={saved} onClose={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('Fecha y hora'), { target: { value: '2099-04-10T10:00' } })
    await userEvent.type(screen.getByLabelText('Enlace HTTPS de la reunión'), 'https://example.test/sala')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar invitación' }))
    expect(await screen.findByText(/No sabemos si se guardó/)).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar la misma solicitud' }))
    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    expect(schedule.mock.calls[0]).toEqual(schedule.mock.calls[1])
    expect(schedule.mock.calls[0]![1]).toMatch(/^[\da-f-]{36}$/)
  })
  it('reprograma la versión actual y avisa que requiere otra confirmación', async () => {
    const update = vi.spyOn(interviewsApi, 'update').mockResolvedValue({ ...invitation, version: 3 })
    render(<InterviewForm token="sintetico" applicationId="a-1" current={invitation} onSaved={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByText(/deberá confirmar de nuevo/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Fecha y hora'), { target: { value: '2099-04-11T10:00' } })
    await userEvent.click(screen.getByRole('button', { name: 'Guardar invitación' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith('sintetico', 'i-1', 2, expect.objectContaining({ startsAt: toInstant('2099-04-11T10:00') })))
  })
  it('convierte hora local sin aceptar fechas normalizadas y muestra el desfase de la zona', () => {
    expect(toInstant(localDateTime(invitation.startsAt))).toBe(invitation.startsAt.replace('Z', '.000Z'))
    expect(() => toInstant('2099-02-30T10:00')).toThrow()
    expect(() => toInstant('')).toThrow()
    expect(interviewTime(invitation.startsAt, 'America/Guayaquil')).toMatch(/GMT-5/)
  })
})

function Inbox() {
  const value = useInterviewInbox('sintetico', true)
  return <InterviewInboxContext.Provider value={value}><InterviewNotice /></InterviewInboxContext.Provider>
}
it('recupera avisos y mantiene el contador del servidor ante eventos duplicados, lectura y reconexión', async () => {
  const unread = vi.spyOn(interviewsApi, 'unread').mockResolvedValue({ unread: 2 })
  const listeners = new Set<NoticeListener>()
  const live: RealtimeValue = { status: 'connecting', listen: (fn) => { listeners.add(fn); return () => { listeners.delete(fn) } }, emit: (n) => listeners.forEach((fn) => fn(n)) }
  const component = render(<RealtimeContext.Provider value={live}><Inbox /></RealtimeContext.Provider>)
  expect(await screen.findByText('Tienes 2 entrevistas con cambios sin leer.')).toBeInTheDocument()
  for (let i = 0; i < 2; i++) {
    act(() => live.emit({ type: 'interview.changed', interviewId: 'i-1', applicationId: 'a-1' }))
    await waitFor(() => expect(unread).toHaveBeenCalledTimes(i + 2))
  }
  expect(screen.getByText('Tienes 2 entrevistas con cambios sin leer.')).toBeInTheDocument()
  unread.mockResolvedValue({ unread: 0 })
  act(() => live.emit({ type: 'interview.read', interviewId: 'i-1' }))
  await waitFor(() => expect(screen.queryByText(/Tienes 2 entrevistas/)).not.toBeInTheDocument())
  act(() => live.emit({ type: 'reconnected' }))
  await waitFor(() => expect(unread).toHaveBeenCalledTimes(5))
  act(() => window.dispatchEvent(new Event('focus')))
  await waitFor(() => expect(unread).toHaveBeenCalledTimes(6))
  component.rerender(<RealtimeContext.Provider value={{ ...live, status: 'online' }}><Inbox /></RealtimeContext.Provider>)
  await waitFor(() => expect(unread).toHaveBeenCalledTimes(7))
})
