import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/api'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import { CandidateHome } from '@/features/home/candidate-home'
import { ApplyPanel } from './apply-panel'
import { ReceivedApplications } from './received-applications'

// Datos sintéticos: ninguna persona ni empresa es real.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function routes(handlers: Record<string, (init?: RequestInit) => Response>) {
  fetchMock.mockImplementation((url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`
    const handler = Object.entries(handlers).find(([k]) => key.endsWith(k))?.[1]
    return Promise.resolve(handler ? handler(init) : new Response(null, { status: 404 }))
  })
}

const calls = (suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => `${(init as RequestInit | undefined)?.method ?? 'GET'} ${url}`.endsWith(suffix)) as [
    string,
    RequestInit,
  ][]

function as(role: Role, children: ReactNode) {
  const value: AuthContextValue = {
    state: {
      status: 'authenticated',
      accessToken: 'jwt-sintetico',
      expiresAt: Date.now() + 900_000,
      user: { id: 'u-1', email: 'persona@ejemplo.test', role, companyId: role === 'CANDIDATE' ? null : 'c-1', createdAt: '' },
    },
    login: vi.fn(),
    logout: vi.fn(),
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

const consents = (share: boolean) => ({ shareWithRecruiters: share, githubLink: false, updatedAt: null })
const cv = (id: string, uploadedAt = '2026-11-11T15:20:00Z') => ({ id, sizeBytes: 245_760, status: 'UPLOADED', uploadedAt })
const mine = (vacancyId = 'v-1') => ({
  id: 'a-1',
  vacancyId,
  vacancyTitle: 'Desarrollador/a backend Java',
  companyName: 'Andina Cargo',
  vacancyStatus: 'PUBLISHED',
  cvId: 'cv-1',
  appliedAt: '2026-11-11T16:00:00Z',
})

describe('ApplyPanel', () => {
  it('sin CV invita a subirlo antes de postular', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, []),
      'GET /api/v1/candidates/me/consents': () => json(200, consents(false)),
      'GET /api/v1/candidates/me/applications': () => json(200, []),
    })
    render(as('CANDIDATE', <ApplyPanel vacancyId="v-1" companyName="Andina Cargo" />))

    expect(await screen.findByRole('button', { name: 'Subir mi CV' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Postular' })).not.toBeInTheDocument()
  })

  it('pide el permiso en el mismo lugar y luego postula con el CV elegido', async () => {
    let applied = false
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, [cv('cv-2', '2026-11-11T16:00:00Z'), cv('cv-1')]),
      'GET /api/v1/candidates/me/consents': () => json(200, consents(false)),
      'GET /api/v1/candidates/me/applications': () => json(200, applied ? [{ ...mine(), cvId: 'cv-1' }] : []),
      'PUT /api/v1/candidates/me/consents': () => json(200, { ...consents(true), updatedAt: '2026-11-11T16:00:00Z' }),
      'POST /api/v1/vacancies/v-1/applications': () => {
        applied = true
        return json(201, { id: 'a-1' })
      },
    })
    const user = userEvent.setup()
    render(as('CANDIDATE', <ApplyPanel vacancyId="v-1" companyName="Andina Cargo" />))

    // Sin marcar el permiso no se envía nada.
    await user.click(await screen.findByRole('button', { name: 'Postular' }))
    expect(screen.getByRole('alert')).toHaveTextContent('permite que Andina Cargo vea tu CV')
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false)

    const radios = screen.getAllByRole('radio')
    expect(radios[0]).toBeChecked()
    await user.click(radios[1]!)
    await user.click(screen.getByRole('checkbox', { name: /Permito que las empresas/ }))
    await user.click(screen.getByRole('button', { name: 'Postular' }))

    expect(await screen.findByText(/Te postulaste el/)).toBeInTheDocument()
    expect(JSON.parse(calls('PUT /api/v1/candidates/me/consents')[0]![1].body as string)).toEqual({
      shareWithRecruiters: true,
      githubLink: false,
    })
    expect(JSON.parse(calls('POST /api/v1/vacancies/v-1/applications')[0]![1].body as string)).toEqual({ cvId: 'cv-1' })
  })

  it('con el permiso ya dado no lo vuelve a pedir', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, [cv('cv-1')]),
      'GET /api/v1/candidates/me/consents': () => json(200, consents(true)),
      'GET /api/v1/candidates/me/applications': () => json(200, []),
    })
    render(as('CANDIDATE', <ApplyPanel vacancyId="v-1" companyName="Andina Cargo" />))

    expect(await screen.findByText(/Te postulas con tu CV subido el/)).toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('si ya se postuló permite retirarlo tras confirmar', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, [cv('cv-1')]),
      'GET /api/v1/candidates/me/consents': () => json(200, consents(true)),
      'GET /api/v1/candidates/me/applications': () => json(200, [mine()]),
      'DELETE /api/v1/candidates/me/applications/a-1': () => new Response(null, { status: 204 }),
    })
    const user = userEvent.setup()
    render(as('CANDIDATE', <ApplyPanel vacancyId="v-1" companyName="Andina Cargo" />))

    await user.click(await screen.findByRole('button', { name: 'Retirar postulación' }))
    expect(screen.getByText('La empresa dejará de ver tu postulación.')).toBeInTheDocument()
    expect(calls('DELETE /api/v1/candidates/me/applications/a-1')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Retirar postulación' }))
    expect(await screen.findByRole('button', { name: 'Postular' })).toBeInTheDocument()
    expect(calls('DELETE /api/v1/candidates/me/applications/a-1')).toHaveLength(1)
  })
})

describe('Inicio del candidato', () => {
  it('lista las postulaciones; una vacante cerrada queda sin enlace', async () => {
    routes({
      'GET /api/v1/candidates/me/applications': () =>
        json(200, [mine('v-1'), { ...mine('v-2'), id: 'a-2', vacancyTitle: 'Analista de datos', vacancyStatus: 'CLOSED' }]),
    })
    render(as('CANDIDATE', <CandidateHome />))

    const list = await screen.findByRole('list', { name: 'Tus postulaciones' })
    const [open, closed] = within(list).getAllByRole('listitem')
    expect(within(open!).getByRole('link')).toHaveAttribute('href', '/vacantes/v-1')
    expect(open).toHaveTextContent('Recibida')
    expect(within(closed!).queryByRole('link')).not.toBeInTheDocument()
    expect(closed).toHaveTextContent('Vacante cerrada')
  })

  it('sin postulaciones invita a ver vacantes y subir el CV', async () => {
    routes({ 'GET /api/v1/candidates/me/applications': () => json(200, []) })
    render(as('CANDIDATE', <CandidateHome />))

    expect(await screen.findByText('Todavía no tienes postulaciones')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver vacantes' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Subir mi CV' })).toBeInTheDocument()
  })
})

describe('Postulaciones recibidas (empresa)', () => {
  it('muestra el correo solo de quien mantiene el permiso', async () => {
    routes({
      'GET /api/v1/companies/me/vacancies/v-1/applications': () =>
        json(200, [
          { id: 'a-1', appliedAt: '2026-11-11T10:00:00Z', shared: true, candidateEmail: 'ana@ejemplo.test', cvId: 'cv-1' },
          { id: 'a-2', appliedAt: '2026-11-11T11:00:00Z', shared: false, candidateEmail: null, cvId: null },
        ]),
    })
    render(as('RECRUITER', <ReceivedApplications token="jwt-sintetico" vacancyId="v-1" />))

    await waitFor(() => expect(screen.getByText('2 personas')).toBeInTheDocument())
    expect(screen.getByText('ana@ejemplo.test')).toBeInTheDocument()
    expect(screen.getByText('Datos no compartidos')).toBeInTheDocument()
    expect(screen.getByText('La persona retiró el permiso de compartir su CV.')).toBeInTheDocument()
    // Solo quien comparte su CV tiene el botón para verlo.
    expect(screen.getAllByRole('button', { name: /^Ver el CV de/ })).toHaveLength(1)
  })

  it('abre el CV en otra pestaña con el enlace temporal', async () => {
    routes({
      'GET /api/v1/companies/me/vacancies/v-1/applications': () =>
        json(200, [{ id: 'a-1', appliedAt: '2026-11-11T10:00:00Z', shared: true, candidateEmail: 'ana@ejemplo.test', cvId: 'cv-1' }]),
      'GET /api/v1/companies/me/vacancies/v-1/applications/a-1/cv': () =>
        json(200, { url: 'http://almacenamiento.test/cv/x.pdf?firma=1', expiresAt: '2026-11-11T10:05:00Z' }),
    })
    const tab = { opener: {}, location: { href: '' }, close: vi.fn() }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    const user = userEvent.setup()
    render(as('RECRUITER', <ReceivedApplications token="jwt-sintetico" vacancyId="v-1" />))

    await user.click(await screen.findByRole('button', { name: /^Ver el CV de ana@ejemplo.test/ }))

    expect(open).toHaveBeenCalledWith('', '_blank')
    await waitFor(() => expect(tab.location.href).toBe('http://almacenamiento.test/cv/x.pdf?firma=1'))
    expect(tab.opener).toBeNull()
    open.mockRestore()
  })

  it('si el permiso se retiró mientras tanto, cierra la pestaña y lo explica', async () => {
    routes({
      'GET /api/v1/companies/me/vacancies/v-1/applications': () =>
        json(200, [{ id: 'a-1', appliedAt: '2026-11-11T10:00:00Z', shared: true, candidateEmail: 'ana@ejemplo.test', cvId: 'cv-1' }]),
      'GET /api/v1/companies/me/vacancies/v-1/applications/a-1/cv': () =>
        new Response(JSON.stringify({ status: 403, code: 'access.cv-not-shared', detail: 'La persona retiró el permiso de compartir su CV' }), {
          status: 403,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
    })
    const tab = { opener: {}, location: { href: '' }, close: vi.fn() }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    const user = userEvent.setup()
    render(as('RECRUITER', <ReceivedApplications token="jwt-sintetico" vacancyId="v-1" />))

    await user.click(await screen.findByRole('button', { name: /^Ver el CV de/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La persona retiró el permiso de compartir su CV.')
    expect(tab.close).toHaveBeenCalled()
    open.mockRestore()
  })
})
