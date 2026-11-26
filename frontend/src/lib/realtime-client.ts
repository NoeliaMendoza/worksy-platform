import { Client } from '@stomp/stompjs'

/** Aviso del servicio de tiempo real. Solo trae ids: el contenido se pide a la API. */
export type RealtimeNotice =
  | { type: 'message.sent'; applicationId: string; messageId: string }
  | { type: 'application.stage.changed'; applicationId: string; stageChangeId: number }
  | { type: 'interview.changed'; applicationId: string; interviewId: string }
  /** Señal WebRTC de otra persona de una sala de videollamada (solo entre participantes autorizados). */
  | { type: 'call.signal'; interviewId: string; sessionId: string; from: string; role: string; kind: CallSignalKind; data: unknown }
  /** El servicio rechazó una señal propia (token de sala vencido, destinatario ajeno, demasiadas señales). */
  | { type: 'call.error'; message: string }
  /** La conexión volvió tras cortarse: pudo perderse algún aviso, conviene recargar. */
  | { type: 'reconnected' }

export type CallSignalKind = 'hello' | 'offer' | 'answer' | 'ice' | 'bye' | 'state'

export type RealtimeStatus = 'connecting' | 'online' | 'offline'

type Options = {
  userId: string
  /** Token de acceso vigente; se lee en cada conexión (el de la sesión se renueva solo). */
  getToken: () => string
  onNotice: (notice: RealtimeNotice) => void
  onStatus: (status: RealtimeStatus) => void
}

/** VALOR SUPUESTO: reintento cada 5 s si se corta la conexión, y latidos cada 10 s. */
const RECONNECT_MS = 5_000
const HEARTBEAT_MS = 10_000

export type RealtimeConnection = {
  close: () => void
  /**
   * Vuelve a conectar con el token actual. El servicio cierra las conexiones cuando vence su token, así que
   * conviene llamarla cada vez que la sesión renueva el token de acceso.
   */
  renew: () => void
  /** Envía una trama (solo se admite /app/call.signal). Devuelve false si no hay conexión en este momento. */
  send: (destination: string, body: unknown) => boolean
}

/**
 * Abre el WebSocket STOMP en /ws (mismo origen; Vite o Nginx lo llevan al servicio realtime) y se suscribe
 * al tema personal del usuario.
 */
export function openRealtime({ userId, getToken, onNotice, onStatus }: Options): RealtimeConnection {
  let connectedBefore = false
  let renewing = false
  let closed = false
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: RECONNECT_MS,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    beforeConnect: (c) => {
      onStatus('connecting')
      c.connectHeaders = { Authorization: `Bearer ${getToken()}` }
    },
    onConnect: () => {
      client.subscribe(`/topic/user.${userId}`, (frame) => {
        try {
          onNotice(JSON.parse(frame.body) as RealtimeNotice)
        } catch {
          // Un aviso ilegible se ignora: la bandeja se actualiza igual al recargar.
        }
      })
      onStatus('online')
      if (connectedBefore) onNotice({ type: 'reconnected' })
      connectedBefore = true
    },
    // Durante una renovación el corte es buscado y breve: no se anuncia como «sin conexión».
    onWebSocketClose: () => !renewing && onStatus('offline'),
    onStompError: () => onStatus('offline'),
  })
  client.activate()
  return {
    send: (destination, body) => {
      if (closed || !client.connected) return false
      client.publish({ destination, body: JSON.stringify(body), headers: { 'content-type': 'application/json' } })
      return true
    },
    close: () => {
      closed = true
      void client.deactivate()
    },
    renew: () => {
      if (closed || renewing) return
      renewing = true
      // Al reconectar se emite «reconnected», así las pantallas recargan lo que pudo llegar en el corte.
      void client
        .deactivate()
        .then(() => {
          if (!closed) client.activate()
        })
        .finally(() => {
          renewing = false
        })
    },
  }
}
