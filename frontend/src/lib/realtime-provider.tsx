import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { openRealtime, type RealtimeConnection, type RealtimeStatus } from './realtime-client'
import { RealtimeContext, type AppNotice, type NoticeListener, type RealtimeValue } from './realtime-context'

/** Mantiene una sola conexión de tiempo real por sesión y reparte sus avisos. */
export function RealtimeProvider({ userId, token, children }: { userId: string; token: string; children: ReactNode }) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const listeners = useRef(new Set<NoticeListener>())
  const latestToken = useRef(token)
  const connection = useRef<RealtimeConnection | null>(null)

  // Token renovado: se reconecta con él antes de que el servicio cierre la conexión por vencimiento.
  useEffect(() => {
    if (latestToken.current === token) return
    latestToken.current = token
    connection.current?.renew()
  }, [token])

  const emit = useCallback((notice: AppNotice) => listeners.current.forEach((l) => l(notice)), [])
  const listen = useCallback((listener: NoticeListener) => {
    listeners.current.add(listener)
    return () => {
      listeners.current.delete(listener)
    }
  }, [])

  useEffect(() => {
    const opened = openRealtime({ userId, getToken: () => latestToken.current, onNotice: emit, onStatus: setStatus })
    connection.current = opened
    return () => {
      connection.current = null
      opened.close()
    }
  }, [userId, emit])

  const send = useCallback((destination: string, body: unknown) => connection.current?.send(destination, body) ?? false, [])
  const value = useMemo<RealtimeValue>(() => ({ status, emit, listen, send }), [status, emit, listen, send])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}
