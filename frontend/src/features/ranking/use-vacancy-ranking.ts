import { useCallback, useEffect, useState } from 'react'
import { fetchVacancyRanking, type RankingPreview } from '@/mocks/vacancy-ranking'
import type { Decision, VacancyRanking } from './types'

export type RankingState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: VacancyRanking }

/**
 * Carga el ranking de una vacante. Hoy usa el mock; cuando exista el endpoint,
 * solo cambia `fetchVacancyRanking` por la llamada a `/api`.
 */
export function useVacancyRanking(preview: RankingPreview) {
  const [state, setState] = useState<RankingState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    fetchVacancyRanking(preview).then(
      (data) => active && setState({ status: 'ready', data }),
      (err: unknown) =>
        active && setState({ status: 'error', message: err instanceof Error ? err.message : 'Error desconocido' }),
    )
    return () => {
      active = false
    }
  }, [preview, attempt])

  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setAttempt((n) => n + 1)
  }, [])

  const decide = useCallback((candidateId: string, decision: Decision | null) => {
    setState((s) =>
      s.status !== 'ready'
        ? s
        : {
            status: 'ready',
            data: {
              ...s.data,
              candidates: s.data.candidates.map((c) => (c.id === candidateId ? { ...c, decision } : c)),
            },
          },
    )
  }, [])

  return { state, retry, decide }
}
