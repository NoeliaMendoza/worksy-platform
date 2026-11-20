import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import { applicationsApi } from '@/features/application/api'
import { ApplicationUpdatesContext } from '@/features/application/application-updates-context'
import { cvApi } from '@/features/cv/api'
import { interviewsApi, type InterviewItem } from '@/features/interviews/api'
import { messagesApi } from '@/features/messages/api'
import { RealtimeContext, type NoticeListener, type RealtimeValue } from '@/lib/realtime-context'
import { CandidateDashboard } from './candidate-dashboard'

const item: InterviewItem = { interview: { id: 'i-1', applicationId: 'a-1', version: 0, title: 'Entrevista técnica', startsAt: '2099-11-18T15:00:00Z',
  durationMinutes: 30, zoneId: 'America/Guayaquil', mode: 'ONLINE', place: 'https://example.test/sala', note: null, status: 'INVITED', updatedAt: '2026-11-18T15:00:00Z' },
  companyName: 'Empresa sintética', vacancyTitle: 'Backend', candidateEmail: 'persona@ejemplo.test', unread: true }
const application = { id: 'a-1', vacancyId: 'v-1', vacancyTitle: 'Backend', companyName: 'Empresa sintética', vacancyStatus: 'PUBLISHED' as const, cvId: 'cv-1', appliedAt: '2026-11-18T15:00:00Z' }
function view(role: 'CANDIDATE' | 'RECRUITER' = 'CANDIDATE', live?: RealtimeValue, firstVisit = false) {
  const auth: AuthContextValue = { state: { status: 'authenticated', accessToken: 'sintetico', expiresAt: Date.now() + 900000,
    user: { id: 'u-1', email: 'persona@ejemplo.test', role, companyId: role === 'CANDIDATE' ? null : 'c-1', createdAt: '2026-11-18T15:00:00Z' } }, login: vi.fn(), logout: vi.fn() }
  return render(<AuthContext.Provider value={auth}><RealtimeContext.Provider value={live ?? null}>
    <ApplicationUpdatesContext.Provider value={{ state: { status: 'ready', data: { unread: firstVisit ? 0 : 1, updates: firstVisit ? [] : [{ applicationId: 'a-1', vacancyTitle: 'Backend', companyName: 'Empresa sintética', stage: 'REVIEW', changedAt: '2026-11-18T15:00:00Z', unread: 1 }] } }, unread: firstVisit ? 0 : 1, refresh: vi.fn() }}>
      <CandidateDashboard />
    </ApplicationUpdatesContext.Provider></RealtimeContext.Provider></AuthContext.Provider>)
}
beforeEach(() => {
  vi.spyOn(applicationsApi, 'mine').mockResolvedValue([{ ...application, stage: 'REVIEW' }, { ...application, id: 'a-2', vacancyStatus: 'CLOSED', stage: 'RECEIVED' }, { ...application, id: 'a-3', stage: 'HIRED' }, { ...application, id: 'a-4', stage: 'REJECTED' }])
  vi.spyOn(interviewsApi, 'list').mockResolvedValue({ items: [item], hasMore: false })
  vi.spyOn(messagesApi, 'inbox').mockResolvedValue([{ applicationId: 'a-1', vacancyTitle: 'Backend', companyName: 'Empresa sintética', candidateEmail: null, lastMessage: 'Mensaje privado que no se muestra en Inicio', lastSentAt: '2026-11-18T15:00:00Z', lastFromCandidate: false, unread: 2 },
    { applicationId: 'a-2', vacancyTitle: 'Datos', companyName: 'Equipo ficticio', candidateEmail: null, lastMessage: 'Otro mensaje', lastSentAt: '2026-11-18T16:00:00Z', lastFromCandidate: false, unread: 3 }])
  vi.spyOn(cvApi, 'list').mockResolvedValue([{ id: 'cv-1', sizeBytes: 1000, status: 'UPLOADED', uploadedAt: '2026-11-18T15:00:00Z' }])
  vi.spyOn(cvApi, 'consents').mockResolvedValue({ shareWithRecruiters: true, githubLink: false, updatedAt: null })
})
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })

describe('Inicio del candidato', () => {
  it('orienta una primera visita con pasos reales y evita multiplicar paneles vacíos', async () => {
    vi.mocked(applicationsApi.mine).mockResolvedValue([])
    vi.mocked(interviewsApi.list).mockResolvedValue({ items: [], hasMore: false })
    vi.mocked(messagesApi.inbox).mockResolvedValue([])
    vi.mocked(cvApi.list).mockResolvedValue([])
    view('CANDIDATE', undefined, true)
    const guide = await screen.findByRole('region', { name: 'Comienza tu búsqueda' })
    expect(within(guide).getAllByRole('listitem')).toHaveLength(3)
    expect(within(guide).getByRole('link', { name: 'Gestionar mi CV' })).toHaveAttribute('href', '/mi-cv')
    expect(within(guide).getByRole('link', { name: 'Explorar vacantes' })).toHaveAttribute('href', '/vacantes')
    expect(screen.queryByRole('region', { name: 'Tu agenda' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Mensajes pendientes' })).not.toBeInTheDocument()
  })

  it('actualiza procesos y mensajes por WebSocket sin un botón de actualización manual', async () => {
    const listeners = new Set<NoticeListener>()
    const live: RealtimeValue = { status: 'connecting', listen: (fn) => { listeners.add(fn); return () => { listeners.delete(fn) } }, emit: (notice) => listeners.forEach((fn) => fn(notice)) }
    view('CANDIDATE', live)
    await screen.findByText('Entrevista técnica')
    expect(screen.queryByRole('button', { name: 'Actualizar resumen' })).not.toBeInTheDocument()
    vi.mocked(applicationsApi.mine).mockResolvedValue([{ ...application, stage: 'HIRED' }])
    act(() => live.emit({ type: 'application.stage.changed', applicationId: 'a-1', stageChangeId: 2 }))
    await waitFor(() => expect(within(screen.getByRole('region', { name: 'Procesos en curso' })).getByText('0')).toBeInTheDocument())
    vi.mocked(messagesApi.inbox).mockResolvedValue([])
    act(() => live.emit({ type: 'message.sent', applicationId: 'a-1', messageId: 'm-1' }))
    await waitFor(() => expect(within(screen.getByRole('region', { name: 'Mensajes sin leer' })).getByText('0')).toBeInTheDocument())
    expect(screen.getByText('No tienes mensajes sin leer.')).toBeInTheDocument()
  })

  it('cuenta datos reales sin finalizar procesos por el cierre de una vacante', async () => {
    view()
    expect(await screen.findByText('Entrevista técnica')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Procesos en curso' })).getByText('2')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Próximas entrevistas' })).getByText('1')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Mensajes sin leer' })).getByText('5')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Archivos de CV' })).getByText('1 / 5')).toBeInTheDocument()
    expect(screen.getByText('CV y permiso disponibles')).toBeInTheDocument()
    expect(screen.queryByText('Mensaje privado que no se muestra en Inicio')).not.toBeInTheDocument()
  })
  it('da acceso a responder en la agenda sin confirmar ni marcar lecturas desde Inicio', async () => {
    const respond = vi.spyOn(interviewsApi, 'respond')
    const readInterview = vi.spyOn(interviewsApi, 'read')
    const readMessages = vi.spyOn(messagesApi, 'markRead')
    const readProgress = vi.spyOn(applicationsApi, 'markProgressRead')
    view()
    expect(await screen.findByRole('link', { name: 'Revisar invitación y responder' })).toHaveAttribute('href', '/entrevistas')
    expect(screen.getAllByRole('link', { name: 'Abrir conversación' })[0]).toHaveAttribute('href', '/mensajes/a-2')
    expect(respond).not.toHaveBeenCalled(); expect(readInterview).not.toHaveBeenCalled()
    expect(readMessages).not.toHaveBeenCalled(); expect(readProgress).not.toHaveBeenCalled()
    expect(within(screen.getByRole('region', { name: 'Tu agenda' })).getByText(/GMT-5/)).toBeInTheDocument()
  })
  it('un error de agenda no oculta el resto ni simula cero entrevistas', async () => {
    vi.mocked(interviewsApi.list).mockRejectedValueOnce(new Error('sin conexión')).mockResolvedValue({ items: [item], hasMore: false })
    view()
    const summary = screen.getByRole('region', { name: 'Próximas entrevistas' })
    await waitFor(() => expect(within(summary).getByText('—')).toBeInTheDocument())
    expect(within(screen.getByRole('region', { name: 'Procesos en curso' })).getByText('2')).toBeInTheDocument()
    expect(screen.queryByText(/No tienes entrevistas próximas/)).not.toBeInTheDocument()
    await userEvent.click(within(summary).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Entrevista técnica')).toBeInTheDocument()
  })
  it('distingue CV ausente de permisos retirados sin inventar porcentajes', async () => {
    vi.mocked(cvApi.list).mockResolvedValue([])
    const first = view()
    expect(await screen.findByText('Falta subir un CV')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Explorar vacantes' })).toHaveAttribute('href', '/vacantes')
    first.unmount()
    vi.mocked(cvApi.list).mockResolvedValue([{ id: 'cv-1', sizeBytes: 1000, status: 'UPLOADED', uploadedAt: '2026-11-18T15:00:00Z' }])
    vi.mocked(cvApi.consents).mockResolvedValue({ shareWithRecruiters: false, githubLink: false, updatedAt: null })
    view()
    expect(await screen.findByText('Revisa el permiso para compartir')).toBeInTheDocument()
    expect(screen.queryByText('Falta subir un CV')).not.toBeInTheDocument()
  })
  it('muestra tres próximas citas y avisa que el contador es parcial si hay más páginas', async () => {
    vi.mocked(interviewsApi.list).mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...item, interview: { ...item.interview, id: `i-${i}` } })), hasMore: true })
    view()
    const agenda = screen.getByRole('region', { name: 'Tu agenda' })
    await waitFor(() => expect(within(agenda).getAllByRole('listitem')).toHaveLength(3))
    expect(within(screen.getByRole('region', { name: 'Próximas entrevistas' })).getByText('20+')).toBeInTheDocument()
    expect(within(agenda).getByText(/tres primeras citas/)).toBeInTheDocument()
  })
  it('actualiza los datos ante avisos duplicados sin incrementar contadores localmente', async () => {
    const listeners = new Set<NoticeListener>()
    const live: RealtimeValue = { status: 'connecting', listen: (fn) => { listeners.add(fn); return () => { listeners.delete(fn) } }, emit: (notice) => listeners.forEach((fn) => fn(notice)) }
    view('CANDIDATE', live)
    await screen.findByText('Entrevista técnica')
    for (let i = 0; i < 2; i++) {
      act(() => live.emit({ type: 'interview.changed', interviewId: 'i-1', applicationId: 'a-1' }))
      await waitFor(() => expect(interviewsApi.list).toHaveBeenCalledTimes(i + 2))
    }
    expect(within(screen.getByRole('region', { name: 'Próximas entrevistas' })).getByText('1')).toBeInTheDocument()
    act(() => live.emit({ type: 'reconnected' }))
    await waitFor(() => expect(applicationsApi.mine).toHaveBeenCalledTimes(2))
    act(() => window.dispatchEvent(new Event('focus')))
    await waitFor(() => expect(applicationsApi.mine).toHaveBeenCalledTimes(3))
  })
  it('retira del resumen una cita que ya comenzó', async () => {
    const now = Date.parse('2099-11-18T14:59:30Z')
    const clock = vi.spyOn(Date, 'now').mockReturnValue(now)
    vi.useFakeTimers()
    view()
    await act(async () => {})
    expect(screen.getByText('Entrevista técnica')).toBeInTheDocument()
    clock.mockReturnValue(now + 61000)
    await act(async () => { await vi.advanceTimersByTimeAsync(60000) })
    expect(screen.queryByText('Entrevista técnica')).not.toBeInTheDocument()
    expect(screen.getByText(/No tienes entrevistas próximas/)).toBeInTheDocument()
  })
  it('no consulta datos de candidato si el rol no corresponde', async () => {
    view('RECRUITER')
    await act(async () => {})
    expect(applicationsApi.mine).not.toHaveBeenCalled()
    expect(interviewsApi.list).not.toHaveBeenCalled()
    expect(messagesApi.inbox).not.toHaveBeenCalled()
    expect(cvApi.list).not.toHaveBeenCalled()
  })

  it('prioriza una invitación real y ofrece una acción accesible por teclado', async () => {
    view()
    const priority = screen.getByRole('region', { name: 'Acción prioritaria' })
    expect(await within(priority).findByText('Tienes una entrevista por confirmar')).toBeInTheDocument()
    const action = within(priority).getByRole('link', { name: 'Revisar invitación' })
    expect(action).toHaveAttribute('href', '/entrevistas')
    await userEvent.tab()
    expect(action).toHaveFocus()
    expect(screen.queryByRole('button', { name: 'Actualizar resumen' })).not.toBeInTheDocument()
  })

  it('orienta a los mensajes y al CV según los datos disponibles', async () => {
    vi.mocked(interviewsApi.list).mockResolvedValue({ items: [], hasMore: false })
    const first = view()
    const priority = screen.getByRole('region', { name: 'Acción prioritaria' })
    expect(await within(priority).findByRole('link', { name: 'Leer mensajes' })).toHaveAttribute('href', '/mensajes')
    first.unmount()
    vi.mocked(messagesApi.inbox).mockResolvedValue([])
    vi.mocked(cvApi.list).mockResolvedValue([])
    view()
    expect(await within(screen.getByRole('region', { name: 'Acción prioritaria' })).findByRole('link', { name: 'Preparar mi CV' })).toHaveAttribute('href', '/mi-cv')
  })

  it('ordena las postulaciones recientes por fecha de envío y no las confunde con novedades', async () => {
    vi.mocked(applicationsApi.mine).mockResolvedValue([
      { ...application, id: 'a-old', vacancyTitle: 'Oferta anterior', appliedAt: '2026-11-01T15:00:00Z', stage: 'REJECTED' },
      { ...application, id: 'a-latest', vacancyTitle: 'Oferta más reciente', appliedAt: '2026-11-18T15:00:00Z', stage: 'REVIEW' },
      { ...application, id: 'a-middle', vacancyTitle: 'Segunda oferta', appliedAt: '2026-11-16T15:00:00Z', stage: 'HIRED' },
      { ...application, id: 'a-third', vacancyTitle: 'Tercera oferta', appliedAt: '2026-11-14T15:00:00Z', stage: 'RECEIVED' },
    ])
    view()
    const recent = screen.getByRole('region', { name: 'Postulaciones recientes' })
    await within(recent).findByText('Oferta más reciente')
    const rows = within(recent).getAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('Oferta más reciente')
    expect(rows[0]!.querySelector('time')).toHaveAttribute('dateTime', '2026-11-18T15:00:00Z')
    expect(rows[1]).toHaveTextContent('Segunda oferta')
    expect(within(recent).queryByText('Oferta anterior')).not.toBeInTheDocument()
  })

  it('ante fuentes desconocidas no inventa una tarea pendiente ni procesos inexistentes', async () => {
    vi.mocked(interviewsApi.list).mockRejectedValue(new Error('sin datos'))
    vi.mocked(messagesApi.inbox).mockRejectedValue(new Error('sin datos'))
    vi.mocked(cvApi.list).mockRejectedValue(new Error('sin datos'))
    vi.mocked(applicationsApi.mine).mockResolvedValue([])
    view()
    const recent = screen.getByRole('region', { name: 'Postulaciones recientes' })
    expect(await within(recent).findByText('Aún no has enviado postulaciones.')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Acción prioritaria' })).toHaveTextContent('Encuentra tu próxima oportunidad')
    expect(screen.queryByText('Prepara tu CV para postular')).not.toBeInTheDocument()
    expect(within(recent).queryByRole('list')).not.toBeInTheDocument()
  })
})
