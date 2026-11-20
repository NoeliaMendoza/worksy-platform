import { createContext, useContext, useEffect, useRef } from 'react'
import type { RealtimeNotice, RealtimeStatus } from './realtime-client'

/** Avisos en vivo y avisos locales (p. ej., "esta conversación ya se leyó") para toda la app. */
export type AppNotice = RealtimeNotice | { type: 'conversation.read'; applicationId: string }
  | { type: 'application.progress.read'; applicationId: string }
  | { type: 'interview.read'; interviewId: string }

export type NoticeListener = (notice: AppNotice) => void

export type RealtimeValue = {
  status: RealtimeStatus
  /** Escucha avisos; devuelve la función para dejar de escuchar. */
  listen: (listener: NoticeListener) => () => void
  /** Avisa a las demás partes de la app (no sale del navegador). */
  emit: (notice: AppNotice) => void
}

export const RealtimeContext = createContext<RealtimeValue | null>(null)

/** Ejecuta `handler` con cada aviso mientras el componente esté montado. Sin proveedor, no hace nada. */
export function useRealtimeNotices(handler: NoticeListener) {
  const realtime = useContext(RealtimeContext)
  const latest = useRef(handler)
  useEffect(() => {
    latest.current = handler
  })
  const listen = realtime?.listen
  useEffect(() => listen?.((n) => latest.current(n)), [listen])
}

export function useRealtime() {
  return useContext(RealtimeContext)
}
