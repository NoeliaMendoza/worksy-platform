import { AppShell } from '@/components/app-shell'
import { ScreenBoundary } from '@/components/screen-boundary'
import { LogoMark } from '@/components/logo'
import type { AuthUser } from '@/features/auth/api'
import { useAuth } from '@/features/auth/auth-context'
import { AuthProvider } from '@/features/auth/auth-provider'
import { ForgotPasswordPage } from '@/features/auth/forgot-password-page'
import { LoginPage } from '@/features/auth/login-page'
import { RegisterPage } from '@/features/auth/register-page'
import { ResetPasswordPage } from '@/features/auth/reset-password-page'
import { CandidateHome } from '@/features/home/candidate-home'
import { CvPage } from '@/features/cv/cv-page'
import { navigationByRole, roleLabel } from '@/features/home/navigation'
import { PlatformAdminHome } from '@/features/home/platform-admin-home'
import { PlatformUsersPage } from '@/features/admin/users-page'
import { PlatformAuditPage } from '@/features/admin/audit-page'
import { ComingSoon } from '@/features/home/coming-soon'
import { RegisterCompanyPage } from '@/features/company/register-company-page'
import { TeamPage } from '@/features/company/team-page'
import { CandidatesPage } from '@/features/candidates/candidates-page'
import { MessagesPage } from '@/features/messages/messages-page'
import { useUnreadMessages } from '@/features/messages/use-unread-messages'
import { CompanyVacanciesPage } from '@/features/vacancy/company-vacancies-page'
import { PublicLayout, PublicVacanciesPage, PublicVacancyPage } from '@/features/vacancy/public-vacancy-pages'
import { lazy, Suspense, type ReactNode } from 'react'
import { RankingView } from '@/features/ranking/ranking-view'
import { useVacancyRanking } from '@/features/ranking/use-vacancy-ranking'
import { RealtimeProvider } from '@/lib/realtime-provider'
import { ApplicationUpdatesContext, useApplicationUpdates } from '@/features/application/application-updates-context'
import { WorkspaceNotices } from '@/components/workspace-notices'
import { InterviewsPage } from '@/features/interviews/interviews-page'
import { CallPage } from '@/features/call/call-page'
import { InterviewInboxContext, useInterviewInbox } from '@/features/interviews/interview-inbox-context'
import { useLocation } from '@/lib/router'
import type { RankingPreview } from '@/mocks/vacancy-ranking'

const previews: RankingPreview[] = ['listo', 'cargando', 'vacio', 'error']
const CandidateDashboard = lazy(() => import('@/features/home/candidate-dashboard').then((module) => ({ default: module.CandidateDashboard })))
const CompanyDashboard = lazy(() => import('@/features/home/company-dashboard').then((module) => ({ default: module.CompanyDashboard })))
const CompanyVacancyPage = lazy(() => import('@/features/vacancy/company-vacancy-pages').then((module) => ({ default: module.CompanyVacancyPage })))
const NewVacancyPage = lazy(() => import('@/features/vacancy/company-vacancy-pages').then((module) => ({ default: module.NewVacancyPage })))
const VacancyRankingPage = lazy(() => import('@/features/ranking/real-ranking-page').then((module) => ({ default: module.RealRankingPage })))

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
    if (pathname === '/registro') return <RegisterPage />
    if (pathname === '/registro-empresa') return <RegisterCompanyPage />
    const [first, id] = pathname.split('/').filter(Boolean)
    if (first === 'vacantes') {
      // Las vacantes publicadas se ven sin iniciar sesión.
      return <PublicLayout>{id ? <PublicVacancyPage id={id} signedIn={false} /> : <PublicVacanciesPage />}</PublicLayout>
    }
    return <LoginPage />
  }
  return <Home user={state.user} token={state.accessToken} pathname={pathname} />
}

/**
 * Cada rol ve solo lo suyo. Es una separación de interfaz: la API debe aplicar la misma regla
 * en cada endpoint (los datos de candidatos nunca deben llegar a una cuenta de candidato).
 */
function Home({ user, token, pathname }: { user: AuthUser; token: string; pathname: string }) {
  // Candidatos y empresas tienen mensajes: una sola conexión de tiempo real para toda la sesión.
  if (user.role === 'PLATFORM_ADMIN') return <Workspace user={user} token={token} pathname={pathname} />
  return (
    <RealtimeProvider userId={user.id} token={token}>
      <Workspace user={user} token={token} pathname={pathname} />
    </RealtimeProvider>
  )
}

function Workspace({ user, token, pathname }: { user: AuthUser; token: string; pathname: string }) {
  const { logout } = useAuth()
  const unread = useUnreadMessages(token, user.role !== 'PLATFORM_ADMIN')
  const updates = useApplicationUpdates(token, user.role === 'CANDIDATE')
  const interviews = useInterviewInbox(token, user.role !== 'PLATFORM_ADMIN')
  const nav = navigationByRole[user.role].map((item) => item.id === 'mensajes' ? { ...item, badge: unread }
    : item.id === 'postulaciones' ? { ...item, badge: updates.unread }
    : item.id === 'entrevistas' ? { ...item, badge: interviews.unread } : item)
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
      <ApplicationUpdatesContext.Provider value={user.role === 'CANDIDATE' ? updates : null}>
        <InterviewInboxContext.Provider value={user.role !== 'PLATFORM_ADMIN' ? interviews : null}>
          <AppShell {...shell} current={pending.id}>
            <WorkspaceNotices unreadMessages={unread} />
            <ComingSoon section={pending.label} />
          </AppShell>
        </InterviewInboxContext.Provider>
      </ApplicationUpdatesContext.Provider>
    )
  }
  const resolved = page ?? pageFor(user.role, '')!
  return (
    <ApplicationUpdatesContext.Provider value={user.role === 'CANDIDATE' ? updates : null}>
      <InterviewInboxContext.Provider value={user.role !== 'PLATFORM_ADMIN' ? interviews : null}>
        <AppShell {...shell} current={resolved.current}>
          <WorkspaceNotices unreadMessages={unread} />
          <ScreenBoundary><Suspense fallback={<div className="app-page text-sm text-ink-500" role="status">Cargando página…</div>}>{resolved.element}</Suspense></ScreenBoundary>
        </AppShell>
      </InterviewInboxContext.Provider>
    </ApplicationUpdatesContext.Provider>
  )
}

/** Pantalla de cada ruta por rol. null = la ruta existe en el menú pero aún no tiene pantalla. */
function pageFor(role: AuthUser['role'], section: string): { current: string; element: ReactNode } | null {
  const [first = '', id, sub] = section.split('/')
  if (role !== 'PLATFORM_ADMIN' && first === 'entrevistas' && id && sub === 'sala')
    return { current: 'entrevistas', element: <CallPage key={id} interviewId={id} /> }
  if (role !== 'PLATFORM_ADMIN' && first === 'entrevistas') return { current: 'entrevistas', element: <InterviewsPage /> }
  if (role === 'CANDIDATE') {
    if (first === '' || first === 'inicio') return { current: 'inicio', element: <ScreenBoundary><Suspense fallback={<div className="app-page text-sm text-ink-500" role="status">Cargando inicio…</div>}><CandidateDashboard /></Suspense></ScreenBoundary> }
    if (first === 'postulaciones') return { current: 'postulaciones', element: <CandidateHome /> }
    if (first === 'mi-cv') return { current: 'mi-cv', element: <CvPage /> }
    if (first === 'mensajes') return { current: 'mensajes', element: <MessagesPage applicationId={id} /> }
    if (first === 'vacantes')
      return { current: 'vacantes', element: id ? <PublicVacancyPage key={id} id={id} signedIn /> : <PublicVacanciesPage /> }
    return null
  }
  if (role === 'PLATFORM_ADMIN') {
    if (first === 'usuarios') return { current: 'usuarios', element: <PlatformUsersPage /> }
    if (first === 'auditoria') return { current: 'auditoria', element: <PlatformAuditPage /> }
    return first === '' || first === 'empresas' ? { current: 'empresas', element: <PlatformAdminHome /> } : null
  }
  if (first === 'equipo') return { current: 'equipo', element: <TeamPage /> }
  if (first === 'candidatos') return { current: 'candidatos', element: <CandidatesPage applicationId={id} /> }
  if (first === 'inicio') return { current: 'inicio', element: <CompanyDashboard /> }
  if (first === 'mensajes') return { current: 'mensajes', element: <MessagesPage applicationId={id} /> }
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
