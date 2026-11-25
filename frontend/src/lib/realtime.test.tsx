import { render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { openRealtime } from './realtime-client'
import { RealtimeProvider } from './realtime-provider'

// Cliente STOMP falso: registra qué hace la conexión sin abrir sockets.
const stomp = vi.hoisted(() => ({ instances: [] as FakeClient[] }))
type Handler = (...args: unknown[]) => void
type FakeClient = {
  config: { beforeConnect: Handler; onConnect: Handler; onWebSocketClose: Handler }
  connectHeaders: Record<string, string>
  active: boolean
  activate: () => void
  deactivate: () => Promise<void>
  subscribe: ReturnType<typeof vi.fn>
}
vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(function (this: FakeClient, config: FakeClient['config']) {
    this.config = config
    this.connectHeaders = {}
    this.active = false
    this.subscribe = vi.fn()
    this.activate = () => {
      this.active = true
      config.beforeConnect(this)
      config.onConnect()
    }
    this.deactivate = async () => {
      this.active = false
      config.onWebSocketClose()
    }
    stomp.instances.push(this)
  }),
}))

const actual = await vi.importActual<typeof import('./realtime-client')>('./realtime-client')

beforeEach(() => {
  stomp.instances.length = 0
  vi.mocked(openRealtime).mockClear()
})

describe('conexión de tiempo real', () => {
  it('al renovar vuelve a conectar con el token nuevo, avisa la reconexión y no se muestra sin conexión', async () => {
    let token = 'token-1'
    const notices: unknown[] = []
    const statuses: string[] = []
    const connection = actual.openRealtime({
      userId: 'u-1',
      getToken: () => token,
      onNotice: (n) => notices.push(n),
      onStatus: (s) => statuses.push(s),
    })
    const client = stomp.instances[0]!
    expect(client.connectHeaders.Authorization).toBe('Bearer token-1')
    expect(client.subscribe).toHaveBeenCalledWith('/topic/user.u-1', expect.any(Function))

    token = 'token-2'
    statuses.length = 0
    connection.renew()
    await waitFor(() => expect(client.connectHeaders.Authorization).toBe('Bearer token-2'))
    expect(notices).toContainEqual({ type: 'reconnected' })
    expect(statuses).not.toContain('offline')
  })

  it('después de cerrar, renovar no vuelve a abrir la conexión', async () => {
    const connection = actual.openRealtime({ userId: 'u-1', getToken: () => 't', onNotice: vi.fn(), onStatus: vi.fn() })
    const client = stomp.instances[0]!
    connection.close()
    connection.renew()
    await Promise.resolve()
    expect(client.active).toBe(false)
  })

  it('el proveedor renueva la conexión cuando cambia el token, no al montarse', () => {
    const { rerender } = render(
      <RealtimeProvider userId="u-1" token="t-1">
        <span />
      </RealtimeProvider>,
    )
    const opened = vi.mocked(openRealtime).mock.results.at(-1)!.value as { renew: ReturnType<typeof vi.fn> }
    expect(opened.renew).not.toHaveBeenCalled()

    rerender(
      <RealtimeProvider userId="u-1" token="t-2">
        <span />
      </RealtimeProvider>,
    )
    expect(opened.renew).toHaveBeenCalledTimes(1)
    expect(vi.mocked(openRealtime)).toHaveBeenCalledTimes(1)
  })
})
