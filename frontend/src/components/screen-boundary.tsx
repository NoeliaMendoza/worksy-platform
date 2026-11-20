import { Component, type ReactNode } from 'react'
import { FileWarning } from 'lucide-react'
import { StateMessage } from './state-message'
import { Button } from './ui/button'

/** Aísla fallos de carga de una pantalla sin perder la navegación del espacio de trabajo. */
export class ScreenBoundary extends Component<{ children: ReactNode; onRetry?: () => void }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() { return { failed: true } }

  render() {
    if (!this.state.failed) return this.props.children
    return <div className="app-page"><StateMessage tone="error" icon={FileWarning} title="No se pudo cargar esta pantalla"
      description="Comprueba tu conexión y vuelve a cargar la aplicación. Puedes seguir usando las otras secciones del menú."
      action={<Button onClick={this.props.onRetry ?? (() => window.location.reload())}>Recargar aplicación</Button>} /></div>
  }
}
