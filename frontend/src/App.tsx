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
import { ComingSoon } from '@/features/home/coming-soon'
import { RegisterCompanyPage } from '@/features/company/register-company-page'
import { TeamPage } from '@/features/company/team-page'
import { CompanyVacanciesPage } from '@/features/vacancy/company-vacancies-page'
import { CompanyVacancyPage, NewVacancyPage, VacancyRankingPage } from '@/features/vacancy/company-vacancy-pages'
import { PublicLayout, PublicVacanciesPage, PublicVacancyPage } from '@/features/vacancy/public-vacancy-pages'
import type { ReactNode } from 'react'
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
  if (state.status === 'anonymous') {
    if (pathname === '/recuperar') return <ForgotPasswordPage />
    if (pathname === '/registro-empresa') return <RegisterCompanyPage />
    const [first, id] = pathname.split('/').filter(Boolean)
    if (first === 'vacantes') {
      // Las vacantes publicadas se ven sin iniciar sesión.
      return <PublicLayout>{id ? <PublicVacancyPage id={id} signedIn={false} /> : <PublicVacanciesPage />}</PublicLayout>
    }
    return <LoginPage />
  }
  return <Home user={state.user} pathname={pathname} />
}

/**
 * Cada rol ve solo lo suyo. Es una separación de interfaz: la API debe aplicar la misma regla
 * en cada endpoint (los datos de candidatos nunca deben llegar a una cuenta de candidato).
 */
function Home({ user, pathname }: { user: AuthUser; pathname: string }) {
  const { logout } = useAuth()
  const nav = navigationByRole[user.role]
  const section = pathname.replace(/^\/+|\/+$/g, '')
  const shell = {
    nav,
    account: { name: user.email, detail: roleLabel[user.role] },
    onLogout: () => void logout(),
  }
  const page = pageFor(user.role, section)
  // Una ruta del menú sin pantalla todavía: se marca en el menú y se avisa con claridad.
  const pending = nav.find((item) => item.id === section.split('/')[0] && page === null)

  if (pending) {
    return (
      <AppShell {...shell} current={pending.id}>
        <ComingSoon section={pending.label} />
      </AppShell>
    )
  }
  const resolved = page ?? pageFor(user.role, '')!
  return (
    <AppShell {...shell} current={resolved.current}>
      {resolved.element}
    </AppShell>
  )
}

/** Pantalla de cada ruta por rol. null = la ruta existe en el menú pero aún no tiene pantalla. */
function pageFor(role: AuthUser['role'], section: string): { current: string; element: ReactNode } | null {
  const [first = '', id, sub] = section.split('/')
  if (role === 'CANDIDATE') {
    if (first === '' || first === 'postulaciones') return { current: 'postulaciones', element: <CandidateHome /> }
    if (first === 'vacantes')
      return { current: 'vacantes', element: id ? <PublicVacancyPage key={id} id={id} signedIn /> : <PublicVacanciesPage /> }
    return null
  }
  if (role === 'PLATFORM_ADMIN') {
    return first === '' || first === 'empresas' ? { current: 'empresas', element: <PlatformAdminHome /> } : null
  }
  if (first === 'equipo') return { current: 'equipo', element: <TeamPage /> }
  if (first === '' || first === 'vacantes') {
    const element = !id ? (
      <CompanyVacanciesPage />
    ) : id === 'nueva' ? (
      <NewVacancyPage />
    ) : id === 'ejemplo' ? (
      <ExampleRanking />
    ) : sub === 'ranking' ? (
      <VacancyRankingPage key={id} id={id} />
    ) : (
      <CompanyVacancyPage key={id} id={id} />
    )
    return { current: 'vacantes', element }
  }
  return null
}

/** Ranking de ejemplo con datos sintéticos: muestra el diseño completo mientras no hay CV reales. */
function ExampleRanking() {
  return (
    <>
      <p className="mx-4 mt-6 rounded-card bg-mid-100 px-4 py-3 text-sm text-mid-700 sm:mx-8">
        <strong>Ejemplo con datos sintéticos.</strong> Ninguna persona ni empresa es real; sirve para ver cómo será el
        ranking cuando lleguen CV.
      </p>
      <RecruiterRanking />
    </>
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
