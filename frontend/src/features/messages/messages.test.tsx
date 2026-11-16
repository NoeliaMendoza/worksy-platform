import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '@/App'
import type { Role } from '@/features/auth/api'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import { openRealtime } from '@/lib/realtime-client'
import { RealtimeProvider } from '@/lib/realtime-provider'
import { MessagesPage } from './messages-page'

// Datos sintéticos: dominio reservado .test.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  vi.mocked(openRealtime).mockClear()
})

afterEach(() => {
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

const json = (status: number, body: unknown) =>
  new Response(body === null ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** Respuestas por "MÉTODO ruta"; una función permite cambiar la respuesta entre llamadas. */
function routes(handlers: Record<string, (init?: RequestInit) => Response>) {
  fetchMock.mockImplementation((url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`
    const handler = Object.entries(handlers).find(([k]) => key.endsWith(k))?.[1]
    return Promise.resolve(handler ? handler(init) : new Response(null, { status: 404 }))
  })
}

const calls = (suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => `${(init as RequestInit | undefined)?.method ?? 'GET'} ${url}`.endsWith(suffix))

function renderPage(role: Role, applicationId?: string) {
  const value: AuthContextValue = {
    state: {
      status: 'authenticated',
      accessToken: 'jwt-sintetico',
      expiresAt: Date.now() + 900_000,
      user: { id: 'u-1', email: 'yo@ejemplo.test', role, companyId: role === 'CANDIDATE' ? null : 'c-1', createdAt: '' },
    },
    login: vi.fn(),
    logout: vi.fn(),
  }
  return render(
    <AuthContext.Provider value={value}>
      <RealtimeProvider userId="u-1" token="jwt-sintetico">
        <MessagesPage applicationId={applicationId} />
      </RealtimeProvider>
    </AuthContext.Provider>,
  )
}

/** Simula un aviso del servicio de tiempo real. */
function notify(notice: Parameters<Parameters<typeof openRealtime>[0]['onNotice']>[0]) {
  const options = vi.mocked(openRealtime).mock.calls.at(-1)![0]
  act(() => options.onNotice(notice))
}

const conversation = {
  applicationId: 'app-1',
  vacancyTitle: 'Backend Java',
  companyName: 'Andina Cargo',
  candidateEmail: 'ana@ejemplo.test',
  lastMessage: 'Sí, el lunes a las 10.',
  lastSentAt: '2026-11-15T15:00:00Z',
  lastFromCandidate: true,
  unread: 2,
}

const fromCompany = {
  id: 'm-1', applicationId: 'app-1', body: 'Hola Ana, ¿podemos hablar el lunes?',
  sentAt: '2026-11-15T14:00:00Z', fromCandidate: false, mine: true,
}
const fromAna = {
  id: 'm-2', applicationId: 'app-1', body: 'Sí, el lunes a las 10.',
  sentAt: '2026-11-15T15:00:00Z', fromCandidate: true, mine: false,
}

describe('MessagesPage', () => {
  it('la persona candidata sin mensajes ve cómo funcionan', async () => {
    routes({ 'GET /api/v1/conversations': () => json(200, []) })
    renderPage('CANDIDATE')
    expect(await screen.findByText('Aún no tienes mensajes')).toBeInTheDocument()
    expect(screen.getByText(/Cuando una empresa te escriba/)).toBeInTheDocument()
  })

  it('la empresa ve la bandeja, abre la conversación, la marca como leída y responde', async () => {
    const sent = { id: 'm-3', applicationId: 'app-1', body: 'Perfecto, te envío la invitación.', sentAt: '2026-11-15T16:00:00Z', fromCandidate: false, mine: true }
    routes({
      'GET /api/v1/conversations': () => json(200, [conversation]),
      'GET /api/v1/applications/app-1/messages': () => json(200, { messages: [fromCompany, fromAna], hasMore: false, canWrite: true }),
      'POST /api/v1/applications/app-1/messages/read': () => json(204, null),
      'POST /api/v1/applications/app-1/messages': () => json(201, sent),
    })
    const user = userEvent.setup()
    renderPage('COMPANY_ADMIN', 'app-1')

    const inbox = await screen.findByRole('region', { name: 'Conversaciones' })
    const row = await within(inbox).findByRole('link', { name: /ana@ejemplo\.test/ })
    expect(row).toHaveAttribute('aria-current', 'page')
    expect(row).toHaveTextContent('2 sin leer')

    const log = await screen.findByRole('log', { name: 'Mensajes con ana@ejemplo.test' })
    expect(within(log).getByText('Hola Ana, ¿podemos hablar el lunes?')).toBeInTheDocument()
    expect(within(log).getByText('ana@ejemplo.test:')).toHaveClass('sr-only')
    await waitFor(() => expect(calls('POST /api/v1/applications/app-1/messages/read')).toHaveLength(1))

    await user.type(screen.getByLabelText('Escribe un mensaje'), 'Perfecto, te envío la invitación.{Enter}')
    expect(await within(log).findByText('Perfecto, te envío la invitación.')).toBeInTheDocument()
    const [, init] = calls('POST /api/v1/applications/app-1/messages')[0] as [string, RequestInit]
    expect(JSON.parse(init.body as string)).toEqual({ body: 'Perfecto, te envío la invitación.' })
    expect(screen.getByLabelText('Escribe un mensaje')).toHaveValue('')
  })

  it('la persona candidata no puede escribir hasta que la empresa lo haga', async () => {
    routes({
      'GET /api/v1/conversations': () => json(200, []),
      'GET /api/v1/applications/app-1/messages': () => json(200, { messages: [], hasMore: false, canWrite: false }),
      'POST /api/v1/applications/app-1/messages/read': () => json(204, null),
    })
    renderPage('CANDIDATE', 'app-1')
    expect(await screen.findByText('Podrás responder cuando la empresa te escriba por esta postulación.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Escribe un mensaje')).not.toBeInTheDocument()
  })

  it('un aviso en vivo trae el mensaje nuevo sin recargar', async () => {
    let page = { messages: [fromCompany], hasMore: false, canWrite: true }
    routes({
      'GET /api/v1/conversations': () => json(200, [{ ...conversation, unread: 0 }]),
      'GET /api/v1/applications/app-1/messages': () => json(200, page),
      'POST /api/v1/applications/app-1/messages/read': () => json(204, null),
    })
    renderPage('COMPANY_ADMIN', 'app-1')
    const log = await screen.findByRole('log')
    await within(log).findByText('Hola Ana, ¿podemos hablar el lunes?')

    page = { messages: [fromCompany, fromAna], hasMore: false, canWrite: true }
    notify({ type: 'message.sent', applicationId: 'app-1', messageId: 'm-2' })
    expect(await within(log).findByText('Sí, el lunes a las 10.')).toBeInTheDocument()
  })

  it('una conversación que ya no existe lo dice con claridad', async () => {
    routes({
      'GET /api/v1/conversations': () => json(200, []),
      'GET /api/v1/applications/app-9/messages': () => json(404, { code: 'application.not-found', detail: 'La postulación no existe' }),
    })
    renderPage('RECRUITER', 'app-9')
    expect(await screen.findByText('Esta conversación no existe')).toBeInTheDocument()
  })

  it('muestra el error de la API si no se puede enviar', async () => {
    routes({
      'GET /api/v1/conversations': () => json(200, [conversation]),
      'GET /api/v1/applications/app-1/messages': () => json(200, { messages: [fromCompany], hasMore: false, canWrite: true }),
      'POST /api/v1/applications/app-1/messages/read': () => json(204, null),
      'POST /api/v1/applications/app-1/messages': () =>
        json(403, { code: 'access.cv-not-shared', detail: 'La persona retiró el permiso de compartir sus datos' }),
    })
    const user = userEvent.setup()
    renderPage('COMPANY_ADMIN', 'app-1')
    await user.type(await screen.findByLabelText('Escribe un mensaje'), 'Hola de nuevo')
    await user.click(screen.getByRole('button', { name: 'Enviar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La persona retiró el permiso de compartir sus datos.')
    expect(screen.getByLabelText('Escribe un mensaje')).toHaveValue('Hola de nuevo')
  })
})

describe('Mensajes en el menú', () => {
  it('el menú cuenta los mensajes sin leer y lleva a la bandeja', async () => {
    const tokens = {
      accessToken: 'jwt-sintetico',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 900_000).toISOString(),
      user: { id: 'u-1', email: 'ana@ejemplo.test', role: 'CANDIDATE', companyId: null, createdAt: '' },
    }
    routes({
      'POST /api/v1/auth/refresh': () => json(200, tokens),
      'GET /api/v1/candidates/me/applications': () => json(200, []),
      'GET /api/v1/conversations': () => json(200, [{ ...conversation, unread: 3 }]),
    })
    const user = userEvent.setup()
    render(<App />)
    const nav = (await screen.findAllByRole('navigation', { name: 'Principal' }))[0]!
    const link = await within(nav).findByRole('link', { name: 'Mensajes, 3 sin leer' })
    expect(vi.mocked(openRealtime)).toHaveBeenCalledWith(expect.objectContaining({ userId: 'u-1' }))

    await user.click(link)
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Tus mensajes')
    expect(within(nav).getByRole('link', { name: /Mensajes/ })).toHaveAttribute('aria-current', 'page')
  })
})
