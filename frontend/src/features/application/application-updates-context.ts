import { createContext, useEffect } from 'react'
import { useResource, type Resource } from '@/lib/use-resource'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { applicationsApi, type ApplicationUpdatesInbox } from './api'

type UpdatesValue = { state: Resource<ApplicationUpdatesInbox>; unread: number; refresh: () => void }
export const ApplicationUpdatesContext = createContext<UpdatesValue | null>(null)

/** Los avisos solo invalidan la consulta: el contador siempre viene de datos persistidos en la API. */
export function useApplicationUpdates(token: string, enabled: boolean): UpdatesValue {
  const status = useRealtime()?.status
  const { state, refresh } = useResource(
    () => enabled ? applicationsApi.updates(token) : Promise.resolve({ unread: 0, updates: [] }),
    `application-updates:${enabled}:${token}`, 'No se pudieron consultar los avisos de tus postulaciones.',
  )
  useRealtimeNotices((notice) => {
    if (enabled && ['application.stage.changed', 'application.progress.read', 'reconnected'].includes(notice.type)) refresh()
  })
  useEffect(() => {
    if (!enabled) return
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [enabled, refresh])
  // Cubre cambios ocurridos entre la consulta inicial y la primera suscripción al WebSocket.
  useEffect(() => {
    if (enabled && status === 'online') refresh()
  }, [enabled, status, refresh])
  return { state, unread: enabled && state.status === 'ready' ? state.data.unread : 0, refresh }
}
