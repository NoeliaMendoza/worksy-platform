import { AppShell } from '@/components/app-shell'
import { LogoMark } from '@/components/logo'
import type { Role } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { AuthProvider } from '@/features/auth/auth-provider'
import { ForgotPasswordPage } from '@/features/auth/forgot-password-page'
import { LoginPage } from '@/features/auth/login-page'
import { ResetPasswordPage } from '@/features/auth/reset-password-page'
import { useLocation } from '@/lib/router'
import { RankingView } from '@/features/ranking/ranking-view'
import { useVacancyRanking } from '@/features/ranking/use-vacancy-ranking'
import type { RankingPreview } from '@/mocks/vacancy-ranking'

const previews: RankingPreview[] = ['listo', 'cargando', 'vacio', 'error']

const roleLabel: Record<Role, string> = {
  CANDIDATE: 'Candidato/a',
  RECRUITER: 'Reclutador/a',
  COMPANY_ADMIN: 'Administración de empresa',
  PLATFORM_ADMIN: 'Administración de Worksy',
}

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
  return <RecruiterHome email={state.user.email} role={state.user.role} />
}

function RecruiterHome({ email, role }: { email: string; role: Role }) {
  const { logout } = useAuth()
  const { state, retry, decide } = useVacancyRanking(previewFromUrl())
  return (
    <AppShell current="vacantes" account={{ name: email, detail: roleLabel[role] }} onLogout={() => void logout()}>
      <RankingView state={state} onRetry={retry} onDecide={decide} />
    </AppShell>
  )
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
