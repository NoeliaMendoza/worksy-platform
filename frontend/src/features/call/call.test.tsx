import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '@/features/auth/auth-context'
import { interviewsApi, type InterviewItem } from '@/features/interviews/api'
import { RealtimeContext, type NoticeListener, type RealtimeValue } from '@/lib/realtime-context'
import { callApi, type CallJoin } from './api'
import { CallPage } from './call-page'
import { PeerLink } from './peer-link'

// Datos sintéticos. jsdom no tiene cámara ni WebRTC: se simulan con lo mínimo que usa la sala.
vi.mock('@/features/auth/auth-context', () => ({ useAuth: vi.fn() }))

type Handler = ((e: unknown) => void) | null
class FakePeerConnection {
  static all: FakePeerConnection[] = []
  signalingState: RTCSignalingState = 'stable'
  connectionState: RTCPeerConnectionState = 'new'
  localDescription: { type: string; sdp: string; toJSON: () => unknown } | null = null
  remote: RTCSessionDescriptionInit | null = null
  onnegotiationneeded: Handler = null
  onicecandidate: Handler = null
  ontrack: Handler = null
  onconnectionstatechange: Handler = null
  senders: { track: { kind: string } | null; replaceTrack: ReturnType<typeof vi.fn> }[] = []
  candidates: unknown[] = []
  closed = false
  constructor(public config: RTCConfiguration) { FakePeerConnection.all.push(this) }
  addTrack(track: { kind: string }) {
    this.senders.push({ track, replaceTrack: vi.fn() })
    queueMicrotask(() => this.onnegotiationneeded?.({}))
  }
  addTransceiver() { queueMicrotask(() => this.onnegotiationneeded?.({})) }
  getSenders() { return this.senders }
  async setLocalDescription() {
    const type = this.remote?.type === 'offer' && this.signalingState === 'have-remote-offer' ? 'answer' : 'offer'
    this.signalingState = type === 'offer' ? 'have-local-offer' : 'stable'
    this.localDescription = { type, sdp: `sdp-${type}`, toJSON: () => ({ type, sdp: `sdp-${type}` }) }
  }
  async setRemoteDescription(d: RTCSessionDescriptionInit) {
    this.remote = d
    this.signalingState = d.type === 'offer' ? 'have-remote-offer' : 'stable'
  }
  async addIceCandidate(c: unknown) { this.candidates.push(c) }
  restartIce() {}
  close() { this.closed = true; this.connectionState = 'closed' }
}

function fakeStream() {
  const track = (kind: string) => ({ kind, enabled: true, stop: vi.fn(), addEventListener: vi.fn() })
  const tracks = [track('audio'), track('video')]
  return { getTracks: () => tracks, getAudioTracks: () => tracks.filter((t) => t.kind === 'audio'), getVideoTracks: () => tracks.filter((t) => t.kind === 'video') }
}

const item: InterviewItem = {
  interview: { id: 'i-1', applicationId: 'a-1', version: 1, title: 'Entrevista técnica', startsAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    durationMinutes: 45, zoneId: 'America/Guayaquil', mode: 'VIDEO', place: 'Videollamada en Worksy', note: null, status: 'CONFIRMED', updatedAt: '2026-11-25T12:00:00Z' },
  vacancyTitle: 'Backend', companyName: 'Empresa sintética', candidateEmail: null, unread: false, phase: 'ACTIVE', changeRequest: null,
}
const joined: CallJoin = { sessionId: 's-1', interviewId: 'i-1', role: 'CANDIDATE', roomToken: 'token-de-sala', expiresAt: '2099-01-01T00:00:00Z',
  iceServers: [], peers: [{ userId: 'u-empresa', role: 'INTERVIEWER', label: 'seleccion@ejemplo.test', present: false }] }

let stream: ReturnType<typeof fakeStream>
let listeners: Set<NoticeListener>
let live: RealtimeValue & { send: ReturnType<typeof vi.fn> }

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({ state: { status: 'authenticated', accessToken: 'sintetico', expiresAt: 9999999999,
    user: { id: 'u-1', email: 'persona@ejemplo.test', role: 'CANDIDATE', companyId: null, createdAt: '2026-01-01T00:00:00Z' } }, login: vi.fn(), logout: vi.fn() })
  stream = fakeStream()
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {
    getUserMedia: vi.fn(async () => stream), enumerateDevices: vi.fn(async () => [{ kind: 'audioinput', deviceId: 'm1', label: 'Micrófono sintético' }]),
    addEventListener: vi.fn(), removeEventListener: vi.fn() } })
  vi.stubGlobal('RTCPeerConnection', FakePeerConnection)
  FakePeerConnection.all = []
  vi.spyOn(interviewsApi, 'get').mockResolvedValue(item)
  vi.spyOn(callApi, 'status').mockResolvedValue({ joinable: true, unavailableReason: null, opensAt: '', closesAt: '', present: [] })
  vi.spyOn(callApi, 'join').mockResolvedValue(joined)
  vi.spyOn(callApi, 'leave').mockResolvedValue(undefined)
  vi.spyOn(callApi, 'leaveOnUnload').mockImplementation(() => undefined)
  listeners = new Set()
  live = { status: 'online', listen: (fn) => { listeners.add(fn); return () => { listeners.delete(fn) } }, emit: (n) => listeners.forEach((fn) => fn(n)), send: vi.fn(() => true) }
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

const page = () => render(<RealtimeContext.Provider value={live}><CallPage interviewId="i-1" /></RealtimeContext.Provider>)
const signals = () => live.send.mock.calls.map(([, body]) => body as { to: string; kind: string; token: string; data: Record<string, unknown> })

describe('antes de entrar', () => {
  it('pide la cámara, muestra el estado de la sala y no graba', async () => {
    page()
    expect(await screen.findByText('Cámara y micrófono listos')).toBeInTheDocument()
    expect(await screen.findByText(/La sala está abierta. Todavía no entró nadie más/)).toBeInTheDocument()
    expect(screen.getByText(/Worksy no graba la llamada/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unirme a la entrevista' })).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Silenciar micrófono' }))
    expect(stream.getAudioTracks()[0]!.enabled).toBe(false)
  })

  it('explica qué hacer si el navegador no tiene permiso', async () => {
    vi.mocked(navigator.mediaDevices.getUserMedia).mockRejectedValue(new DOMException('no', 'NotAllowedError'))
    page()
    expect(await screen.findByText(/no tiene permiso para usar tu cámara/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Volver a intentar' })).toBeInTheDocument()
  })

  it('no deja entrar si la sala todavía no abre', async () => {
    vi.mocked(callApi.status).mockResolvedValue({ joinable: false, unavailableReason: 'La sala abre 15 minutos antes de la hora de la entrevista', opensAt: '', closesAt: '', present: [] })
    page()
    expect(await screen.findByText(/La sala abre 15 minutos antes/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unirme a la entrevista' })).toBeDisabled()
  })
})

describe('en la llamada', () => {
  it('entra por la API, saluda, negocia con quien responde y al salir libera todo', async () => {
    const user = userEvent.setup()
    page()
    await user.click(await screen.findByRole('button', { name: 'Unirme a la entrevista' }))
    expect(callApi.join).toHaveBeenCalledWith('sintetico', 'i-1')
    expect(await screen.findByRole('region', { name: 'Videollamada en curso' })).toBeInTheDocument()
    await waitFor(() => expect(signals()).toContainEqual(expect.objectContaining({ to: 'u-empresa', kind: 'hello', token: 'token-de-sala' })))
    expect(screen.getByText(/Esperando a que seleccion@ejemplo.test entre/)).toBeInTheDocument()

    // La empresa entra y saluda: se crea la conexión, se responde una vez y se negocia.
    act(() => live.emit({ type: 'call.signal', interviewId: 'i-1', sessionId: 's-1', from: 'u-empresa', role: 'INTERVIEWER', kind: 'hello', data: { mic: true, cam: true } }))
    await waitFor(() => expect(FakePeerConnection.all).toHaveLength(1))
    await waitFor(() => expect(signals()).toContainEqual(expect.objectContaining({ kind: 'hello', data: expect.objectContaining({ reply: true }) })))
    await waitFor(() => expect(signals().some((s) => s.kind === 'offer')).toBe(true))

    // Señales de otra entrevista o de alguien ajeno se ignoran.
    act(() => live.emit({ type: 'call.signal', interviewId: 'otra', sessionId: 'x', from: 'u-empresa', role: 'INTERVIEWER', kind: 'hello', data: null }))
    act(() => live.emit({ type: 'call.signal', interviewId: 'i-1', sessionId: 's-1', from: 'u-ajena', role: 'INTERVIEWER', kind: 'hello', data: null }))
    expect(FakePeerConnection.all).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Salir de la llamada' }))
    expect(await screen.findByText('Saliste de la entrevista')).toBeInTheDocument()
    expect(signals()).toContainEqual(expect.objectContaining({ kind: 'bye', to: 'u-empresa' }))
    expect(FakePeerConnection.all[0]!.closed).toBe(true)
    expect(callApi.leave).toHaveBeenCalledWith('sintetico', 'i-1')
    stream.getTracks().forEach((t) => expect(t.stop).toHaveBeenCalled())
  })
})

describe('negociación', () => {
  const offer = { type: 'offer' as const, sdp: 'sdp-ajeno' }
  async function link(polite: boolean) {
    const sent: { kind: string; data: unknown }[] = []
    const l = new PeerLink({ polite, iceServers: [], stream: null, send: (s) => sent.push(s), onRemoteStream: vi.fn(), onState: vi.fn() })
    // Deja que termine la negociación inicial (como en un navegador real) antes de recibir señales.
    await new Promise((r) => setTimeout(r, 0))
    return { l, sent, pc: FakePeerConnection.all.at(-1)! }
  }
  beforeEach(() => vi.stubGlobal('RTCPeerConnection', FakePeerConnection))

  it('si las dos partes ofrecen a la vez, la educada cede y responde; la otra ignora la oferta', async () => {
    const polite = await link(true)
    await polite.pc.setLocalDescription()
    await polite.l.receive('offer', offer)
    expect(polite.sent).toContainEqual({ kind: 'answer', data: { type: 'answer', sdp: 'sdp-answer' } })

    const impolite = await link(false)
    await impolite.pc.setLocalDescription()
    await impolite.l.receive('offer', offer)
    expect(impolite.sent.some((s) => s.kind === 'answer')).toBe(false)
    await impolite.l.receive('ice', { candidate: 'x' })
  })

  it('la parte educada responde aunque ya haya ofrecido y aplica los candidatos', async () => {
    const { l, sent, pc } = await link(true)
    await l.receive('offer', offer)
    await l.receive('ice', { candidate: 'candidato sintético' })
    expect(sent).toContainEqual({ kind: 'answer', data: { type: 'answer', sdp: 'sdp-answer' } })
    expect(pc.candidates).toEqual([{ candidate: 'candidato sintético' }])
    l.close()
    expect(pc.closed).toBe(true)
  })
})
