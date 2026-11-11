import {
  BriefcaseBusiness,
  Building2,
  FileText,
  LayoutGrid,
  MessageSquare,
  ShieldCheck,
  Users,
  Video,
} from 'lucide-react'
import type { NavItem } from '@/components/app-shell'
import type { Role } from '@/features/auth/api'

/** Quien recluta: vacantes, candidatos y su revisión. */
const recruiting: NavItem[] = [
  { id: 'inicio', label: 'Inicio', icon: LayoutGrid },
  { id: 'vacantes', label: 'Vacantes', icon: BriefcaseBusiness },
  { id: 'candidatos', label: 'Candidatos', icon: Users },
  { id: 'entrevistas', label: 'Entrevistas', icon: Video },
  { id: 'mensajes', label: 'Mensajes', icon: MessageSquare },
]

/** Quien busca empleo: sus postulaciones y su CV. Nunca ve rankings ni datos de otras personas. */
const candidate: NavItem[] = [
  { id: 'postulaciones', label: 'Mis postulaciones', icon: FileText },
  { id: 'vacantes', label: 'Vacantes', icon: BriefcaseBusiness },
  { id: 'entrevistas', label: 'Entrevistas', icon: Video },
  { id: 'mensajes', label: 'Mensajes', icon: MessageSquare },
]

/** Administración de la plataforma: empresas y cuentas. */
const platform: NavItem[] = [
  { id: 'empresas', label: 'Empresas', icon: Building2 },
  { id: 'usuarios', label: 'Usuarios', icon: Users },
  { id: 'auditoria', label: 'Auditoría', icon: ShieldCheck },
]

export const navigationByRole: Record<Role, NavItem[]> = {
  RECRUITER: recruiting,
  COMPANY_ADMIN: recruiting,
  CANDIDATE: candidate,
  PLATFORM_ADMIN: platform,
}

export const roleLabel: Record<Role, string> = {
  CANDIDATE: 'Candidato/a',
  RECRUITER: 'Reclutador/a',
  COMPANY_ADMIN: 'Administración de empresa',
  PLATFORM_ADMIN: 'Administración de Worksy',
}
