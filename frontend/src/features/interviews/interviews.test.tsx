import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { RealtimeContext, type NoticeListener, type RealtimeValue } from '@/lib/realtime-context'
import { interviewsApi, type Interview, type InterviewItem, type InterviewSummary } from './api'
import { InterviewForm } from './interview-form'
import { InterviewsPage } from './interviews-page'
import { InterviewInboxContext, useInterviewInbox } from './interview-inbox-context'
import { InterviewNotice } from './interview-notice'
import { interviewTime, localDateTime, toInstant, viewerZone, zoneLabel } from './interview-time'

// Datos sintéticos.
vi.mock('@/features/auth/auth-context', () => ({ useAuth: vi.fn() }))
const invitation: Interview = { id: 'i-1', applicationId: 'a-1', version: 2, title: 'Entrevista técnica', startsAt: '2099-04-10T15:00:00Z',
  durationMinutes: 30, zoneId: 'America/Guayaquil', mode: 'ONLINE', place: 'https://example.test/sala', note: 'Preparar preguntas.',
  status: 'INVITED', cancelReason: null, outcomeAt: null, updatedAt: '2099-04-01T12:00:00Z' }
const row: InterviewItem = { interview: invitation, companyName: 'Empresa sintética', vacancyTitle: 'Backend', candidateEmail: null,
  organizerEmail: null, unread: true, mine: false, phase: 'ACTIVE', changeRequest: null }
const companyRow: InterviewItem = { ...row, candidateEmail: 'persona@ejemplo.test', organizerEmail: 'seleccion@ejemplo.test', mine: true }
const summary: InterviewSummary = { counts: { upcoming: 1, pending: 1, history: 0, cancelled: 0, changeRequests: 0, awaitingOutcome: 0, expired: 0 }, next: row }

function auth(company = false) {
  vi.mocked(useAuth).mockReturnValue({ state: { status: 'authenticated', accessToken: 'sintetico', expiresAt: 9999999999,
    user: { id: 'u-1', email: 'persona@ejemplo.test', role: company ? 'RECRUITER' : 'CANDIDATE', companyId: company ? 'c-1' : null, createdAt: '2026-01-01T00:00:00Z' } }, login: vi.fn(), logout: vi.fn() })
}
function serve(item: InterviewItem) {
  vi.spyOn(interviewsApi, 'list').mockResolvedValue({ items: [item], hasMore: false })
  vi.spyOn(interviewsApi, 'get').mockResolvedValue(item)
}

beforeEach(() => {
  window.history.replaceState(null, '', '/entrevistas')
  auth()
  serve(row)
  vi.spyOn(interviewsApi, 'summary').mockResolvedValue(summary)
  vi.spyOn(interviewsApi, 'history').mockResolvedValue([])
  vi.spyOn(interviewsApi, 'read').mockResolvedValue(undefined)
  vi.spyOn(interviewsApi, 'overlaps').mockResolvedValue([])
})
afterEach(() => vi.restoreAllMocks())

describe('entrevistas del candidato', () => {
  it('muestra la agenda con su detalle y solo marca como leída la cita que se abre', async () => {
    render(<InterviewsPage />)
    expect(await within(await screen.findByRole('complementary')).findByRole('heading', { name: 'Entrevista técnica' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Por confirmar 1/ })).toBeInTheDocument()
    // La zona es la del equipo que ejecuta la prueba (en CI, UTC): no se fija ninguna.
    expect(screen.getByText(zoneLabel(viewerZone()))).toBeInTheDocument()
    expect(interviewsApi.read).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: /Entrevista técnica.*Por confirmar/ }))
    await waitFor(() => expect(interviewsApi.read).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2))
    expect(window.location.search).toBe('?cita=i-1')
  })

  it('no marca como leída en una pestaña oculta y lo hace al volver', async () => {
    window.history.replaceState(null, '', '/entrevistas?cita=i-1')
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    render(<InterviewsPage />)
    await within(await screen.findByRole('complementary')).findByRole('heading', { name: 'Entrevista técnica' })
    expect(interviewsApi.read).not.toHaveBeenCalled()
    visibility.mockReturnValue('visible')
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    await waitFor(() => expect(interviewsApi.read).toHaveBeenCalledWith('sintetico', 'i-1', 2))
  })

  it('confirma con la versión vista y no ofrece acciones de la empresa', async () => {
    const respond = vi.spyOn(interviewsApi, 'respond').mockResolvedValue({ ...invitation, version: 3, status: 'CONFIRMED' })
    render(<InterviewsPage />)
    await userEvent.click(await within(await screen.findByRole('complementary')).findByRole('button', { name: 'Confirmar asistencia' }))
    expect(await screen.findByText('Confirmaste tu asistencia.')).toBeInTheDocument()
    expect(respond).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2, 'CONFIRMED')
    expect(screen.queryByRole('button', { name: 'Cancelar entrevista' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reprogramar o editar' })).not.toBeInTheDocument()
  })

  it('avisa que no puede asistir con un motivo opcional después de confirmar la decisión', async () => {
    const respond = vi.spyOn(interviewsApi, 'respond').mockResolvedValue({ ...invitation, version: 3, status: 'DECLINED' })
    render(<InterviewsPage />)
    await userEvent.click(await within(await screen.findByRole('complementary')).findByRole('button', { name: 'No puedo asistir' }))
    expect(respond).not.toHaveBeenCalled()
    await userEvent.type(screen.getByLabelText('Motivo (opcional)'), 'Me enfermé')
    await userEvent.click(screen.getByRole('button', { name: 'Avisar que no asistiré' }))
    await waitFor(() => expect(respond).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2, 'DECLINED', 'Me enfermé'))
  })

  it('propone otra hora y reintenta con el mismo identificador si se pierde la respuesta', async () => {
    const request = vi.spyOn(interviewsApi, 'requestChange').mockRejectedValueOnce(new ApiError(0, 'network', 'Red'))
      .mockResolvedValue({ ...invitation, version: 3 })
    render(<InterviewsPage />)
    await userEvent.click(await within(await screen.findByRole('complementary')).findByRole('button', { name: 'Proponer otra hora' }))
    fireEvent.change(screen.getByLabelText('Horario 1'), { target: { value: '2099-05-01T10:00' } })
    await userEvent.type(screen.getByLabelText('Motivo (lo verá la empresa)'), 'Tengo un examen')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar propuesta' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Enviar propuesta' }))
    await waitFor(() => expect(request).toHaveBeenCalledTimes(2))
    expect(request.mock.calls[0]).toEqual(request.mock.calls[1])
    expect(request.mock.calls[0]!.slice(0, 3)).toEqual(['sintetico', 'i-1', 2])
    expect(request.mock.calls[0]![4]).toBe('Tengo un examen')
    expect(request.mock.calls[0]![5]).toEqual([toInstant('2099-05-01T10:00')])
  })

  it('tras un conflicto no repite la acción y ofrece ver la versión actual', async () => {
    const respond = vi.spyOn(interviewsApi, 'respond').mockRejectedValue(new ApiError(409, 'interview.conflict', 'Cambió'))
    render(<InterviewsPage />)
    await userEvent.click(await within(await screen.findByRole('complementary')).findByRole('button', { name: 'Confirmar asistencia' }))
    expect(await screen.findByText(/cambió mientras respondías/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar asistencia' })).toBeDisabled()
    vi.mocked(interviewsApi.get).mockClear()
    await userEvent.click(screen.getByRole('button', { name: 'Ver la versión actual' }))
    await waitFor(() => expect(interviewsApi.get).toHaveBeenCalled())
    expect(screen.getByRole('button', { name: 'Confirmar asistencia' })).toBeEnabled()
    expect(respond).toHaveBeenCalledOnce()
  })

  it('cambia de pestaña y muestra un estado vacío que explica qué esperar', async () => {
    render(<InterviewsPage />)
    await screen.findByRole('complementary')
    vi.mocked(interviewsApi.list).mockResolvedValue({ items: [], hasMore: false })
    await userEvent.click(screen.getByRole('tab', { name: /Canceladas/ }))
    expect(await screen.findByText('No hay entrevistas canceladas.')).toBeInTheDocument()
    expect(interviewsApi.list).toHaveBeenLastCalledWith('sintetico', 'cancelled', 0, false)
  })

  it('descarga el archivo .ics para agregar la cita a cualquier calendario', async () => {
    const file = vi.spyOn(interviewsApi, 'calendarFile').mockResolvedValue(new Blob(['BEGIN:VCALENDAR'], { type: 'text/calendar' }))
    const created = vi.fn(() => 'blob:sintetico')
    Object.assign(URL, { createObjectURL: created, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    render(<InterviewsPage />)
    await userEvent.click(await within(await screen.findByRole('complementary')).findByRole('button', { name: 'Agregar a mi calendario' }))
    expect(await screen.findByText(/Descargamos el archivo de la cita/)).toBeInTheDocument()
    expect(file).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1')
    expect(created).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
  })
})

describe('entrevistas de la empresa', () => {
  beforeEach(() => {
    auth(true)
    serve(companyRow)
  })

  it('cancela solo con un motivo visible para la persona', async () => {
    const cancel = vi.spyOn(interviewsApi, 'cancel').mockResolvedValue({ ...invitation, version: 3, status: 'CANCELLED', cancelReason: 'La vacante se pausó' })
    render(<InterviewsPage />)
    const panel = await screen.findByRole('complementary')
    expect(await within(panel).findByText('persona@ejemplo.test, Backend')).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Confirmar asistencia' })).not.toBeInTheDocument()
    await userEvent.click(within(panel).getByRole('button', { name: 'Cancelar entrevista' }))
    await userEvent.click(within(panel).getByRole('button', { name: 'Cancelar entrevista' }))
    expect(cancel).not.toHaveBeenCalled()
    await userEvent.type(within(panel).getByLabelText('Motivo (lo verá la persona candidata)'), 'La vacante se pausó')
    await userEvent.click(within(panel).getByRole('button', { name: 'Cancelar entrevista' }))
    await waitFor(() => expect(cancel).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2, 'La vacante se pausó'))
  })

  it('acepta una de las horas propuestas por la persona', async () => {
    serve({ ...companyRow, changeRequest: { id: 'cr-1', reason: 'Tengo un examen', slots: ['2099-04-12T15:00:00Z', '2099-04-13T15:00:00Z'], createdAt: '2099-04-02T10:00:00Z' } })
    const accept = vi.spyOn(interviewsApi, 'acceptChange').mockResolvedValue({ ...invitation, version: 3, status: 'CONFIRMED' })
    render(<InterviewsPage />)
    const panel = await screen.findByRole('complementary')
    expect(await within(panel).findByText('«Tengo un examen»')).toBeInTheDocument()
    await userEvent.click(within(panel).getAllByRole('radio')[1]!)
    await userEvent.click(within(panel).getByRole('button', { name: /^Aceptar/ }))
    await waitFor(() => expect(accept).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2, 'cr-1', 1))
  })

  it('registra el resultado cuando falta y aclara que no decide el proceso', async () => {
    serve({ ...companyRow, interview: { ...invitation, status: 'CONFIRMED', startsAt: '2020-04-10T15:00:00Z' }, phase: 'AWAITING_OUTCOME' })
    const outcome = vi.spyOn(interviewsApi, 'outcome').mockResolvedValue({ ...invitation, version: 3, status: 'NO_SHOW' })
    render(<InterviewsPage />)
    const panel = await screen.findByRole('complementary')
    expect(await within(panel).findByText(/no cambia la etapa ni decide el proceso/)).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Cancelar entrevista' })).not.toBeInTheDocument()
    await userEvent.click(within(panel).getByRole('button', { name: 'No asistió' }))
    await waitFor(() => expect(outcome).toHaveBeenCalledExactlyOnceWith('sintetico', 'i-1', 2, 'NO_SHOW'))
  })

  it('filtra las citas a mi cargo', async () => {
    render(<InterviewsPage />)
    await screen.findByRole('complementary')
    await userEvent.click(screen.getByLabelText('Solo las que tengo a cargo'))
    await waitFor(() => expect(interviewsApi.list).toHaveBeenLastCalledWith('sintetico', 'upcoming', 0, true))
    expect(interviewsApi.summary).toHaveBeenLastCalledWith('sintetico', true)
  })

  it('valida el contexto de la postulación antes de permitir agendar', async () => {
    window.history.replaceState(null, '', '/entrevistas?postulacion=ajena')
    vi.spyOn(interviewsApi, 'context').mockRejectedValue(new ApiError(404, 'interview.not-found', 'No existe'))
    render(<InterviewsPage />)
    expect(await screen.findByText(/No existe/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar invitación' })).not.toBeInTheDocument()
  })
})

describe('formulario de entrevista', () => {
  it('reutiliza el identificador y los datos exactos tras perder la respuesta de creación', async () => {
    const schedule = vi.spyOn(interviewsApi, 'schedule').mockRejectedValueOnce(new ApiError(0, 'network', 'Red')).mockResolvedValue(invitation)
    const saved = vi.fn()
    render(<InterviewForm token="sintetico" applicationId="a-1" onSaved={saved} onClose={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('Fecha y hora'), { target: { value: '2099-04-10T10:00' } })
    // Por defecto es la sala de Worksy: no hay enlace que pegar.
    expect(screen.queryByLabelText('Enlace HTTPS de la reunión')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Enviar invitación' }))
    expect(await screen.findByText(/No sabemos si se guardó/)).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar la misma solicitud' }))
    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    expect(schedule.mock.calls[0]).toEqual(schedule.mock.calls[1])
    expect(schedule.mock.calls[0]![1]).toMatch(/^[\da-f-]{36}$/)
  })

  it('avisa si reprogramar pide otra confirmación y si se cruza con otra cita propia', async () => {
    vi.mocked(interviewsApi.overlaps).mockResolvedValue([{ interviewId: 'i-9', title: 'Entrevista técnica', startsAt: '2099-04-11T15:00:00Z',
      durationMinutes: 45, zoneId: 'America/Guayaquil', vacancyTitle: 'QA' }])
    const update = vi.spyOn(interviewsApi, 'update').mockResolvedValue({ ...invitation, version: 3 })
    render(<InterviewForm token="sintetico" applicationId="a-1" current={invitation} onSaved={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByText(/se conserva su respuesta/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Fecha y hora'), { target: { value: '2099-04-11T10:00' } })
    expect(screen.getByText(/deberá confirmar de nuevo/)).toBeInTheDocument()
    expect(await screen.findByText(/Se cruza con otra entrevista tuya/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/Motivo del cambio/), 'Cambio de agenda')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith('sintetico', 'i-1', 2,
      expect.objectContaining({ startsAt: toInstant('2099-04-11T10:00') }), 'Cambio de agenda'))
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

