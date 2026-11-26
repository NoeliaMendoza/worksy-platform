/**
 * Conexión WebRTC con otra persona de la sala, con el patrón «perfect negotiation»: las dos partes pueden iniciar
 * la negociación y, si chocan, la parte «educada» (la persona candidata) cede. El audio y el video viajan cifrados
 * (DTLS-SRTP) de navegador a navegador; el servidor solo reenvía estas señales y no guarda nada.
 */
export type LinkState = 'connecting' | 'connected' | 'reconnecting' | 'failed' | 'closed'
export type OutgoingSignal = { kind: 'offer' | 'answer' | 'ice'; data: unknown }

type Options = {
  polite: boolean
  iceServers: RTCIceServer[]
  stream: MediaStream | null
  send: (signal: OutgoingSignal) => void
  onRemoteStream: (stream: MediaStream | null) => void
  onState: (state: LinkState) => void
}

/** VALOR SUPUESTO: si la conexión sigue caída 5 s, se reinicia ICE (cambio de red, Wi-Fi intermitente). */
const RESTART_AFTER_MS = 5_000

export class PeerLink {
  readonly pc: RTCPeerConnection
  private makingOffer = false
  private ignoreOffer = false
  private restartTimer: ReturnType<typeof setTimeout> | undefined
  private closed = false

  constructor(private readonly options: Options) {
    this.pc = new RTCPeerConnection({ iceServers: options.iceServers })
    const pc = this.pc
    options.stream?.getTracks().forEach((track) => pc.addTrack(track, options.stream!))
    // Aunque no haya cámara o micrófono propio, se recibe lo de la otra persona.
    if (!options.stream?.getAudioTracks().length) pc.addTransceiver('audio', { direction: 'recvonly' })
    if (!options.stream?.getVideoTracks().length) pc.addTransceiver('video', { direction: 'recvonly' })

    pc.onnegotiationneeded = async () => {
      try {
        this.makingOffer = true
        await pc.setLocalDescription()
        if (pc.localDescription) options.send({ kind: 'offer', data: pc.localDescription.toJSON() })
      } catch {
        // La negociación se repetirá con la próxima señal o al reiniciar ICE.
      } finally {
        this.makingOffer = false
      }
    }
    pc.onicecandidate = ({ candidate }) => {
      if (candidate) options.send({ kind: 'ice', data: candidate.toJSON() })
    }
    pc.ontrack = ({ streams }) => options.onRemoteStream(streams[0] ?? null)
    pc.onconnectionstatechange = () => this.stateChanged()
  }

  /** Procesa una oferta, una respuesta o un candidato ICE de la otra persona. */
  async receive(kind: 'offer' | 'answer' | 'ice', data: unknown) {
    if (this.closed) return
    const pc = this.pc
    if (kind === 'ice') {
      try {
        await pc.addIceCandidate(data as RTCIceCandidateInit)
      } catch (err) {
        if (!this.ignoreOffer) throw err
      }
      return
    }
    const description = data as RTCSessionDescriptionInit
    const collision = description.type === 'offer' && (this.makingOffer || pc.signalingState !== 'stable')
    this.ignoreOffer = !this.options.polite && collision
    if (this.ignoreOffer) return
    await pc.setRemoteDescription(description)
    if (description.type === 'offer') {
      await pc.setLocalDescription()
      if (pc.localDescription) this.options.send({ kind: 'answer', data: pc.localDescription.toJSON() })
    }
  }

  /** Cambia la cámara o el micrófono sin renegociar (misma pista, otro dispositivo). */
  async replaceTrack(track: MediaStreamTrack) {
    const sender = this.pc.getSenders().find((s) => s.track?.kind === track.kind)
    if (sender) await sender.replaceTrack(track)
    else if (this.options.stream) this.pc.addTrack(track, this.options.stream)
  }

  restartIce() {
    if (!this.closed) this.pc.restartIce()
  }

  close() {
    this.closed = true
    clearTimeout(this.restartTimer)
    this.pc.onnegotiationneeded = null
    this.pc.onicecandidate = null
    this.pc.ontrack = null
    this.pc.onconnectionstatechange = null
    this.pc.close()
    this.options.onState('closed')
  }

  private stateChanged() {
    const state = this.pc.connectionState
    clearTimeout(this.restartTimer)
    if (state === 'connected') this.options.onState('connected')
    else if (state === 'disconnected') {
      this.options.onState('reconnecting')
      this.restartTimer = setTimeout(() => this.restartIce(), RESTART_AFTER_MS)
    } else if (state === 'failed') {
      this.options.onState('reconnecting')
      this.restartIce()
    } else if (state === 'new' || state === 'connecting') this.options.onState('connecting')
  }
}
