import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from './http'

export type Resource<T> =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; data: T }

/**
 * Carga un recurso de la API con estados de carga, error y reintento.
 * `key` identifica la petición: al cambiar, se vuelve a cargar.
 */
export function useResource<T>(load: () => Promise<T>, key: string, fallbackError: string) {
  const [state, setState] = useState<{ key: string; value: Resource<T> }>({ key, value: { status: 'loading' } })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    load().then(
      (data) => active && setState({ key, value: { status: 'ready', data } }),
      (err: unknown) => active && setState({ key, value: { status: 'error', message: errorMessage(err, fallbackError) } }),
    )
    return () => {
      active = false
    }
    // `load` cambia en cada render; `key` y `attempt` deciden cuándo volver a pedir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt])

  const retry = useCallback(() => {
    setState((s) => ({ ...s, value: { status: 'loading' } }))
    setAttempt((n) => n + 1)
  }, [])

  /** Sustituye los datos ya cargados (p. ej., tras guardar) sin volver a pedirlos. */
  const replace = useCallback((data: T) => setState((s) => ({ ...s, value: { status: 'ready', data } })), [])

  /** Aplica una respuesta sobre los datos actuales, aunque haya terminado otra petición mientras tanto. */
  const update = useCallback((change: (current: T) => T) => setState((s) =>
    s.value.status === 'ready' ? { ...s, value: { status: 'ready', data: change(s.value.data) } } : s,
  ), [])

  const refresh = useCallback(() => setAttempt((n) => n + 1), [])

  // Mientras llega la respuesta de una clave nueva, se muestra "cargando" en vez de datos viejos.
  const value: Resource<T> = state.key === key ? state.value : { status: 'loading' }
  return { state: value, retry, refresh, replace, update }
}
