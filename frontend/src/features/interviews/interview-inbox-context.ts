import { createContext, useEffect } from 'react'
import { useResource } from '@/lib/use-resource'
import { useRealtime, useRealtimeNotices } from '@/lib/realtime-context'
import { interviewsApi } from './api'

export const InterviewInboxContext = createContext<{ unread: number; failed: boolean; refresh: () => void } | null>(null)

export function useInterviewInbox(token: string, enabled: boolean) {
  const { state, refresh } = useResource(() => enabled ? interviewsApi.unread(token) : Promise.resolve({ unread: 0 }),
    `interview-inbox:${enabled}:${token}`, 'No se pudieron consultar los avisos de entrevistas.')
  const status = useRealtime()?.status
  useRealtimeNotices((notice) => {
    if (enabled && ['interview.changed', 'interview.read', 'reconnected'].includes(notice.type)) refresh()
  })
  useEffect(() => {
    if (enabled && status === 'online') refresh()
  }, [enabled, status, refresh])
  useEffect(() => {
    if (!enabled) return
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [enabled, refresh])
  return { unread: enabled && state.status === 'ready' ? state.data.unread : 0, failed: enabled && state.status === 'error', refresh }
}
