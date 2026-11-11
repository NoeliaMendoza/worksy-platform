import { apiRequest } from '@/lib/http'

export interface Company {
  id: string
  name: string
  createdAt: string
}

export interface Member {
  id: string
  email: string
  role: 'COMPANY_ADMIN' | 'RECRUITER'
  createdAt: string
}

/** Autoregistro público: crea la empresa y la cuenta de su administración. */
export function registerCompany(companyName: string, email: string, password: string) {
  return apiRequest<{ company: Company; admin: Member }>('/companies', {
    method: 'POST',
    body: { companyName, email, password },
  })
}

export function myCompany(token: string) {
  return apiRequest<Company>('/companies/me', { token })
}

export function members(token: string) {
  return apiRequest<Member[]>('/companies/me/members', { token })
}

/** Solo la administración de la empresa. Envía un enlace de activación por correo. */
export function inviteRecruiter(token: string, email: string) {
  return apiRequest<Member>('/companies/me/members', { method: 'POST', body: { email }, token })
}
