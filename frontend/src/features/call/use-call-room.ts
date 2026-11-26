import { useCallback, useEffect, useRef, useState } from 'react'
import { errorMessage } from '@/lib/http'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import type { CallSignalKind } from '@/lib/realtime-client'
import { callApi, type CallJoin, type CallPeer } from './api'
import { PeerLink, type LinkState } from './peer-link'

export type RoomPhase = 'prejoin' | 'joining' | 'in-call' | 'ended'
export type RemotePeer = CallPeer & { state: LinkState | 'waiting'; stream: MediaStream | null; micOn: boolean; camOn: boolean }

const SIGNAL_DESTINATION = '/app/call.signal'

/**
 * Sala de videollamada: entra por la API (que decide el acceso), intercambia señales por el WebSocket y mantiene una
 * conexión WebRTC por cada otra persona. Se recupera de cortes: al volver la conexión en vivo se saluda de nuevo
 * y, si la conexión directa cae, se reinicia ICE.
 */
export function useCallRoom({ token, interviewId, stream, micOn, camOn }: {
  token: string; interviewId: string; stream: MediaStream | null; micOn: boolean; camOn: boolean
}) {
  const realtime = useRealtime()
  const [phase, setPhase] = useState<RoomPhase>('prejoin')
  const [join, setJoin] = useState<CallJoin | null>(null)
  const [peers, setPeers] = useState<Record<string, RemotePeer>>({})
  const [error, setError] = useState<string | null>(null)
  const [joinedAt, setJoinedAt] = useState<number | null>(null)
  const links = useRef(new Map<string, PeerLink>())
  const info = useRef<CallJoin | null>(null)
  const latest = useRef({ stream, micOn, camOn })
  useEffect(() => { latest.current = { stream, micOn, camOn } })

  const send = useCallback((to: string, kind: CallSignalKind, data: unknown = null) => {
    const room = info.current
    if (!room) return false
    return realtime?.send?.(SIGNAL_DESTINATION, { token: room.roomToken, to, kind, data }) ?? false
  }, [realtime])

  const patch = useCallback((id: string, change: Partial<RemotePeer>) => setPeers((all) => all[id] ? { ...all, [id]: { ...all[id]!, ...change } } : all), [])

  const linkFor = useCallback((peerId: string) => {
    const existing = links.current.get(peerId)
    if (existing && existing.pc.connectionState !== 'closed') return existing
    const room = info.current!
    const link = new PeerLink({
      // La persona candidata cede si las dos partes negocian a la vez.
      polite: room.role === 'CANDIDATE',
      iceServers: room.iceServers.length ? [{ urls: room.iceServers }] : [],
      stream: latest.current.stream,
      send: ({ kind, data }) => send(peerId, kind, data),
      onRemoteStream: (remote) => patch(peerId, { stream: remote }),
      onState: (state) => patch(peerId, { state }),
    })
    links.current.set(peerId, link)
    patch(peerId, { state: 'connecting', present: true })
    return link
  }, [send, patch])

  const greetAll = useCallback(() => {
    const room = info.current
    if (!room) return
    for (const peer of room.peers) send(peer.userId, 'hello', { mic: latest.current.micOn, cam: latest.current.camOn })
  }, [send])

  useRealtimeNotices((notice) => {
    const room = info.current
    if (!room || phase !== 'in-call') return
    if (notice.type === 'reconnected') {
      greetAll()
      return
    }
    if (notice.type === 'call.error') {
      setError(notice.message)
      return
    }
    if (notice.type !== 'call.signal' || notice.interviewId !== interviewId) return
    if (!room.peers.some((p) => p.userId === notice.from)) return
    const from = notice.from
    const data = notice.data as Record<string, unknown> | null
    switch (notice.kind) {
      case 'hello': {
        const fresh = !links.current.has(from) || links.current.get(from)!.pc.connectionState === 'closed'
          || links.current.get(from)!.pc.connectionState === 'failed'
        if (fresh) {
          links.current.get(from)?.close()
          linkFor(from)
        }
        patch(from, { present: true, micOn: data?.mic !== false, camOn: data?.cam !== false })
        // Se responde una sola vez para que quien acaba de entrar sepa que ya estamos.
        if (!data?.reply) send(from, 'hello', { reply: true, mic: latest.current.micOn, cam: latest.current.camOn })
        break
      }
      case 'offer':
      case 'answer':
      case 'ice':
        linkFor(from).receive(notice.kind, notice.data).catch(() => setError('No pudimos conectar el audio y el video. Reintentando…'))
        break
      case 'state':
        patch(from, { micOn: data?.mic !== false, camOn: data?.cam !== false })
        break
      case 'bye':
        links.current.get(from)?.close()
        links.current.delete(from)
        patch(from, { present: false, state: 'waiting', stream: null })
        break
    }
  })

  // Cambios de micrófono o cámara: se avisa a las demás personas (solo el estado, nunca el contenido).
  useEffect(() => {
    if (phase !== 'in-call' || !info.current) return
    for (const peer of info.current.peers) send(peer.userId, 'state', { mic: micOn, cam: camOn })
  }, [micOn, camOn, phase, send])

  // Otro dispositivo elegido: se reemplazan las pistas en cada conexión sin renegociar.
  useEffect(() => {
    if (!stream) return
    for (const link of links.current.values()) stream.getTracks().forEach((t) => void link.replaceTrack(t))
  }, [stream])

  const enter = useCallback(async () => {
    setPhase('joining')
    setError(null)
    try {
      const room = await callApi.join(token, interviewId)
      info.current = room
      setJoin(room)
      setPeers(Object.fromEntries(room.peers.map((p) => [p.userId, { ...p, state: 'waiting' as const, stream: null, micOn: true, camOn: true }])))
      setJoinedAt(Date.now())
      setPhase('in-call')
      // Saludar inicia la conexión con quien ya esté dentro (y avisa a quien entre después).
      queueMicrotask(() => greetAll())
    } catch (err) {
      setError(errorMessage(err, 'No pudimos abrir la sala. Inténtalo de nuevo.'))
      setPhase('prejoin')
    }
  }, [token, interviewId, greetAll])

  const closeAll = useCallback(() => {
    const room = info.current
    if (room) for (const peer of room.peers) send(peer.userId, 'bye')
    for (const link of links.current.values()) link.close()
    links.current.clear()
  }, [send])

  const leave = useCallback(async (forEveryone = false) => {
    closeAll()
    info.current = null
    setPhase('ended')
    try {
      if (forEveryone) await callApi.end(token, interviewId)
      else await callApi.leave(token, interviewId)
    } catch {
      // La sala se cierra sola al terminar su horario; salir localmente ya liberó cámara y micrófono.
    }
  }, [closeAll, token, interviewId])

  // Cerrar la pestaña o salir de la página: se avisa y se libera todo.
  useEffect(() => {
    if (phase !== 'in-call') return
    const onUnload = () => {
      closeAll()
      callApi.leaveOnUnload(token, interviewId)
    }
    window.addEventListener('pagehide', onUnload)
    return () => window.removeEventListener('pagehide', onUnload)
  }, [phase, closeAll, token, interviewId])

  useEffect(() => () => {
    if (info.current) {
      closeAll()
      callApi.leaveOnUnload(token, interviewId)
    }
    // Solo al desmontar la sala.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return { phase, join, peers: Object.values(peers), error, joinedAt, online: realtime?.status === 'online', enter, leave, restart: greetAll }
}
