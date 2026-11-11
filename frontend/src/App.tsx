import { AppShell } from '@/components/app-shell'
import { LogoMark } from '@/components/logo'
import type { AuthUser } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { AuthProvider } from '@/features/auth/auth-provider'
import { ForgotPasswordPage } from '@/features/auth/forgot-password-page'
import { LoginPage } from '@/features/auth/login-page'
import { ResetPasswordPage } from '@/features/auth/reset-password-page'
import { CandidateHome } from '@/features/home/candidate-home'
import { navigationByRole, roleLabel } from '@/features/home/navigation'
import { PlatformAdminHome } from '@/features/home/platform-admin-home'
import { RankingView } from '@/features/ranking/ranking-view'
import { useVacancyRanking } from '@/features/ranking/use-vacancy-ranking'
import { useLocation } from '@/lib/router'
import type { RankingPreview } from '@/mocks/vacancy-ranking'

const previews: RankingPreview[] = ['listo', 'cargando', 'vacio', 'error']

/** Lee ?estado= para revisar los estados de la pantalla sin backend. */
function previewFromUrl(): RankingPreview {
  const value = new URLSearchParams(window.location.search).get('estado')
  return previews.find((p) => p === value) ?? 'listo'
}

export function App() {
  return (
    <AuthProvider>
      <Gate />
    </AuthProvider>
  )
}

function Gate() {
  const { state } = useAuth()
  const { pathname } = useLocation()
  // Restablecer la contraseña funciona con o sin sesión: el enlace del correo puede abrirse en cualquier navegador.
  if (pathname === '/restablecer') return <ResetPasswordPage />
  if (state.status === 'checking') return <Splash />
  if (state.status === 'anonymous') return pathname === '/recuperar' ? <ForgotPasswordPage /> : <LoginPage />
  return <Home user={state.user} />
}

/**
 * Cada rol ve solo lo suyo. Es una separación de interfaz: la API debe aplicar la misma regla
 * en cada endpoint (los datos de candidatos nunca deben llegar a una cuenta de candidato).
 */
function Home({ user }: { user: AuthUser }) {
  const { logout } = useAuth()
  const shell = {
    nav: navigationByRole[user.role],
    account: { name: user.email, detail: roleLabel[user.role] },
    onLogout: () => void logout(),
  }

  if (user.role === 'CANDIDATE') {
    return (
      <AppShell {...shell} current="postulaciones">
        <CandidateHome />
      </AppShell>
    )
  }
  if (user.role === 'PLATFORM_ADMIN') {
    return (
      <AppShell {...shell} current="empresas">
        <PlatformAdminHome />
      </AppShell>
    )
  }
  return (
    <AppShell {...shell} current="vacantes">
      <RecruiterRanking />
    </AppShell>
  )
}

function RecruiterRanking() {
  const { state, retry, decide } = useVacancyRanking(previewFromUrl())
  return <RankingView state={state} onRetry={retry} onDecide={decide} />
}

function Splash() {
  return (
    <div className="grid min-h-dvh place-items-center" role="status">
      <div className="flex flex-col items-center gap-3">
        <LogoMark tone="light" className="size-10 animate-pulse" />
        <p className="text-sm text-ink-500">Comprobando tu sesión…</p>
      </div>
    </div>
  )
}
