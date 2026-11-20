import { useCallback, useEffect, useState } from 'react'
import { useRealtimeNotices } from '@/lib/realtime-context'
import { messagesApi } from './api'

/**
 * Total de mensajes sin leer, para el menú. Se actualiza con cada aviso en vivo y al leer una conversación.
 * Si la API falla, no muestra nada (el menú no debe mostrar errores).
 */
export function useUnreadMessages(token: string, enabled: boolean): number {
  const [unread, setUnread] = useState(0)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    if (!enabled) return
    let active = true
    messagesApi.inbox(token).then(
      (list) => active && setUnread(list.reduce((sum, c) => sum + c.unread, 0)),
      () => undefined,
    )
    return () => {
      active = false
    }
  }, [token, enabled, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  useRealtimeNotices((notice) => {
    if (notice.type === 'message.sent' || notice.type === 'conversation.read' || notice.type === 'reconnected') reload()
  })
  return enabled ? unread : 0
}
