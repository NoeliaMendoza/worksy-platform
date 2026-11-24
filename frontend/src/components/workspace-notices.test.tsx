import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApplicationUpdatesContext } from '@/features/application/application-updates-context'
import { InterviewInboxContext } from '@/features/interviews/interview-inbox-context'
import { WorkspaceNotices } from './workspace-notices'
import { applicationsApi } from '@/features/application/api'

it('agrupa fallos sin duplicar franjas y reintenta las dos consultas', async () => {
  const applications = vi.fn()
  const interviews = vi.fn()
  render(<ApplicationUpdatesContext.Provider value={{ state: { status: 'error', message: 'Sin conexión' }, unread: 0, refresh: applications }}>
    <InterviewInboxContext.Provider value={{ failed: true, unread: 0, refresh: interviews }}><WorkspaceNotices /></InterviewInboxContext.Provider>
  </ApplicationUpdatesContext.Provider>)
  expect(screen.getAllByRole('status')).toHaveLength(1)
  expect(screen.getByText(/postulaciones y entrevistas/)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar avisos' }))
  expect(applications).toHaveBeenCalledOnce()
  expect(interviews).toHaveBeenCalledOnce()
})

it('conserva avisos válidos de entrevistas cuando solo falla el seguimiento', async () => {
  render(<ApplicationUpdatesContext.Provider value={{ state: { status: 'error', message: 'Sin conexión' }, unread: 0, refresh: vi.fn() }}>
    <InterviewInboxContext.Provider value={{ failed: false, unread: 1, refresh: vi.fn() }}><WorkspaceNotices /></InterviewInboxContext.Provider>
  </ApplicationUpdatesContext.Provider>)
  await userEvent.click(screen.getByText('Avisos pendientes'))
  expect(screen.getByText('Tienes 1 entrevista con cambios sin leer.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Revisar entrevistas' })).toBeInTheDocument()
})

it('despliega procesos con fecha y etapa sin marcarlos como leídos', async () => {
  const read = vi.spyOn(applicationsApi,'markProgressRead')
  render(<ApplicationUpdatesContext.Provider value={{state:{status:'ready',data:{unread:2,updates:[{applicationId:'a-2',vacancyTitle:'Backend Java',companyName:'Empresa sintética',stage:'REVIEW',changedAt:'2026-11-20T12:00:00Z',unread:2}]}},unread:2,refresh:vi.fn()}}>
    <InterviewInboxContext.Provider value={null}><WorkspaceNotices /></InterviewInboxContext.Provider>
  </ApplicationUpdatesContext.Provider>)
  expect(screen.getByText('Avisos pendientes').closest('details')).not.toHaveAttribute('open')
  await userEvent.click(screen.getByText('Avisos pendientes'))
  await userEvent.click(screen.getByText('Ver procesos con novedades'))
  const list = screen.getByRole('list',{name:'Procesos con cambios sin leer'})
  expect(within(list).getByRole('link',{name:'Backend Java · Empresa sintética'})).toHaveAttribute('href','/postulaciones?postulacion=a-2')
  expect(list).toHaveTextContent('En revisión')
  expect(read).not.toHaveBeenCalled()
  read.mockRestore()
})

it('ofrece acceso a mensajes sin sumar sus cantidades con otros tipos de aviso', async () => {
  render(<WorkspaceNotices unreadMessages={3}/>)
  await userEvent.click(screen.getByText('Avisos pendientes'))
  expect(screen.getByText('Tienes 3 mensajes sin leer.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button',{name:'Abrir mensajes'}))
  expect(window.location.pathname).toBe('/mensajes')
  window.history.replaceState(null,'','/')
})
