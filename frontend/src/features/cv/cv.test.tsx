import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import { formatSize, precheck } from './api'
import { CvPage } from './cv-page'

// Datos sintéticos: ningún archivo ni persona es real.
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const problem = (status: number, code: string, detail: string) =>
  new Response(JSON.stringify({ status, code, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

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

function asCandidate(children: ReactNode) {
  const value: AuthContextValue = {
    state: {
      status: 'authenticated',
      accessToken: 'jwt-sintetico',
      expiresAt: Date.now() + 900_000,
      user: { id: 'u-1', email: 'cand@ejemplo.test', role: 'CANDIDATE', companyId: null, createdAt: '' },
    },
    login: vi.fn(),
    logout: vi.fn(),
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

const noConsents = { shareWithRecruiters: false, githubLink: false, updatedAt: null }
const cv = (id: string, uploadedAt = '2026-11-11T15:20:00Z') => ({ id, sizeBytes: 245_760, status: 'UPLOADED', uploadedAt })
const pdf = (name = 'cv-sintetico.pdf', size = 2048) =>
  new File([new Uint8Array(size)], name, { type: 'application/pdf' })

describe('Revisión previa del archivo', () => {
  it('avisa antes de subir lo que no es un PDF o pesa demasiado', () => {
    expect(precheck(new File(['x'], 'cv.docx', { type: 'application/msword' }))).toMatch(/no es un PDF/)
    expect(precheck(new File([], 'cv.pdf', { type: 'application/pdf' }))).toMatch(/vacío/)
    expect(precheck(pdf('cv.pdf', 5 * 1024 * 1024 + 1))).toMatch(/máximo es 5 MB/)
    expect(precheck(pdf())).toBeNull()
    expect(formatSize(245_760)).toBe('240 KB')
  })
})

describe('CvPage', () => {
  it('sin CV invita a subirlo y la privacidad empieza desactivada', async () => {
    routes({ 'GET /api/v1/candidates/me/cv': () => json(200, []), 'GET /api/v1/candidates/me/consents': () => json(200, noConsents) })
    render(asCandidate(<CvPage />))

    expect(await screen.findByText('Arrastra tu CV aquí')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Tus archivos' })).not.toBeInTheDocument()
    const toggle = screen.getByRole('switch', { name: 'Compartirlo con las empresas a las que me postule' })
    expect(toggle).toHaveAttribute('aria-checked', 'false')
    expect(toggle).toHaveAccessibleDescription('Ahora solo tú puedes verlo.')
  })

  it('sube el PDF como multipart y lo agrega a la lista', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, []),
      'GET /api/v1/candidates/me/consents': () => json(200, noConsents),
      'POST /api/v1/candidates/me/cv': () => json(201, cv('cv-1')),
    })
    const user = userEvent.setup()
    render(asCandidate(<CvPage />))

    await user.upload(await screen.findByLabelText('Elegir archivo'), pdf())

    const list = await screen.findByRole('list')
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByText('1 de 5')).toBeInTheDocument()
    expect(screen.getByText('PDF, 240 KB')).toBeInTheDocument()
    const [, init] = calls('POST /api/v1/candidates/me/cv')[0]!
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.body as FormData).get('file')).toBeInstanceOf(File)
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
    expect(screen.getByRole('status')).toHaveTextContent('CV subido.')
  })

  it('no envía un archivo que no es PDF y explica qué hacer', async () => {
    routes({ 'GET /api/v1/candidates/me/cv': () => json(200, []), 'GET /api/v1/candidates/me/consents': () => json(200, noConsents) })
    const user = userEvent.setup({ applyAccept: false })
    render(asCandidate(<CvPage />))

    await user.upload(await screen.findByLabelText('Elegir archivo'), new File(['x'], 'cv.docx', { type: 'application/msword' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Exporta tu CV como PDF')
    expect(calls('POST /api/v1/candidates/me/cv')).toHaveLength(0)
  })

  it('muestra el mensaje de la API cuando rechaza el archivo', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, [cv('cv-1')]),
      'GET /api/v1/candidates/me/consents': () => json(200, noConsents),
      'POST /api/v1/candidates/me/cv': () => problem(409, 'cv.duplicate', 'Ya subiste este mismo archivo'),
    })
    const user = userEvent.setup()
    render(asCandidate(<CvPage />))

    await user.upload(await screen.findByLabelText('Elegir archivo'), pdf())

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya subiste este mismo archivo.')
  })

  it('pide confirmación antes de eliminar y permite cancelar', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, [cv('cv-1')]),
      'GET /api/v1/candidates/me/consents': () => json(200, noConsents),
      'DELETE /api/v1/candidates/me/cv/cv-1': () => new Response(null, { status: 204 }),
    })
    const user = userEvent.setup()
    render(asCandidate(<CvPage />))

    await user.click(await screen.findByRole('button', { name: /^Eliminar CV subido el/ }))
    expect(screen.getByText('Se borrará de Worksy de forma definitiva.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Eliminar CV' })).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(calls('DELETE /api/v1/candidates/me/cv/cv-1')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: /^Eliminar CV subido el/ }))
    await user.click(screen.getByRole('button', { name: 'Eliminar CV' }))

    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Tus archivos' })).not.toBeInTheDocument())
    expect(calls('DELETE /api/v1/candidates/me/cv/cv-1')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent('CV eliminado.')
  })

  it('un aviso de subida desaparece cuando cambia la lista', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, [cv('cv-1')]),
      'GET /api/v1/candidates/me/consents': () => json(200, noConsents),
      'POST /api/v1/candidates/me/cv': () => problem(409, 'cv.duplicate', 'Ya subiste este mismo archivo'),
      'DELETE /api/v1/candidates/me/cv/cv-1': () => new Response(null, { status: 204 }),
    })
    const user = userEvent.setup()
    render(asCandidate(<CvPage />))

    await user.upload(await screen.findByLabelText('Elegir archivo'), pdf())
    expect(await screen.findByRole('alert')).toHaveTextContent('Ya subiste este mismo archivo.')

    await user.click(screen.getByRole('button', { name: /^Eliminar CV subido el/ }))
    await user.click(screen.getByRole('button', { name: 'Eliminar CV' }))

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('con 5 CV ya no ofrece subir otro', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, ['a', 'b', 'c', 'd', 'e'].map((id) => cv(id))),
      'GET /api/v1/candidates/me/consents': () => json(200, noConsents),
    })
    render(asCandidate(<CvPage />))

    expect(await screen.findByRole('heading', { name: 'Tienes 5 CV, el máximo' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Elegir archivo')).not.toBeInTheDocument()
  })

  it('guarda el consentimiento y conserva el de GitHub', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => json(200, []),
      'GET /api/v1/candidates/me/consents': () => json(200, { ...noConsents, githubLink: true }),
      'PUT /api/v1/candidates/me/consents': (init) =>
        json(200, { ...JSON.parse(init!.body as string), updatedAt: '2026-11-11T15:30:00Z' }),
    })
    const user = userEvent.setup()
    render(asCandidate(<CvPage />))

    const toggle = await screen.findByRole('switch')
    await user.click(toggle)

    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))
    expect(JSON.parse(calls('PUT /api/v1/candidates/me/consents')[0]![1].body as string)).toEqual({
      shareWithRecruiters: true,
      githubLink: true,
    })
    expect(toggle).toHaveAccessibleDescription(/podrá ver tu CV/)
  })

  it('si falla la carga ofrece reintentar', async () => {
    routes({
      'GET /api/v1/candidates/me/cv': () => problem(503, 'storage.unavailable', 'x'),
      'GET /api/v1/candidates/me/consents': () => json(200, noConsents),
    })
    const user = userEvent.setup()
    render(asCandidate(<CvPage />))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cargar tu CV')
    routes({ 'GET /api/v1/candidates/me/cv': () => json(200, []), 'GET /api/v1/candidates/me/consents': () => json(200, noConsents) })
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Arrastra tu CV aquí')).toBeInTheDocument()
  })
})
