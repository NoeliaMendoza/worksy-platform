import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { openRealtime, type RealtimeStatus } from './realtime-client'
import { RealtimeContext, type AppNotice, type NoticeListener, type RealtimeValue } from './realtime-context'

/** Mantiene una sola conexión de tiempo real por sesión y reparte sus avisos. */
export function RealtimeProvider({ userId, token, children }: { userId: string; token: string; children: ReactNode }) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const listeners = useRef(new Set<NoticeListener>())
  const latestToken = useRef(token)

  useEffect(() => {
    latestToken.current = token
  }, [token])

  const emit = useCallback((notice: AppNotice) => listeners.current.forEach((l) => l(notice)), [])
  const listen = useCallback((listener: NoticeListener) => {
    listeners.current.add(listener)
    return () => {
      listeners.current.delete(listener)
    }
  }, [])

  useEffect(
    () => openRealtime({ userId, getToken: () => latestToken.current, onNotice: emit, onStatus: setStatus }),
    [userId, emit],
  )

  const value = useMemo<RealtimeValue>(() => ({ status, emit, listen }), [status, emit, listen])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}
