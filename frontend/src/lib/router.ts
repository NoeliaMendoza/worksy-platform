import { useSyncExternalStore } from 'react'

/**
 * Enrutado mínimo con la History API: suficiente para las pocas pantallas públicas
 * (inicio de sesión, recuperar y restablecer contraseña) sin añadir una dependencia.
 */
const EVENT = 'worksy:navigate'

function subscribe(onChange: () => void) {
  window.addEventListener('popstate', onChange)
  window.addEventListener(EVENT, onChange)
  return () => {
    window.removeEventListener('popstate', onChange)
    window.removeEventListener(EVENT, onChange)
  }
}

export function navigate(to: string, { replace = false } = {}) {
  if (replace) window.history.replaceState(null, '', to)
  else window.history.pushState(null, '', to)
  window.dispatchEvent(new Event(EVENT))
}

/** Ruta actual (pathname) y sus parámetros de búsqueda; se actualiza al navegar. */
export function useLocation() {
  const href = useSyncExternalStore(subscribe, () => window.location.pathname + window.location.search)
  const url = new URL(href, window.location.origin)
  return { pathname: url.pathname, params: url.searchParams }
}
