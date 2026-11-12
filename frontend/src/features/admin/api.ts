import type { Role } from '@/features/auth/api'
import { apiRequest } from '@/lib/http'

export interface UserOverview {
  id: string
  email: string
  role: Role
  companyId: string | null
  companyName: string | null
  createdAt: string
}

export interface UserPage {
  items: UserOverview[]
  total: number
  page: number
  size: number
}

export function listPlatformUsers(token: string, query: string, role: Role | '', page: number, size = 20) {
  const params = new URLSearchParams({ q: query, role, page: String(page), size: String(size) })
  return apiRequest<UserPage>(`/admin/users?${params}`, { token })
}
