import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApplicationUpdatesContext } from '@/features/application/application-updates-context'
import { InterviewInboxContext } from '@/features/interviews/interview-inbox-context'
import { WorkspaceNotices } from './workspace-notices'

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

it('conserva avisos válidos de entrevistas cuando solo falla el seguimiento', () => {
  render(<ApplicationUpdatesContext.Provider value={{ state: { status: 'error', message: 'Sin conexión' }, unread: 0, refresh: vi.fn() }}>
    <InterviewInboxContext.Provider value={{ failed: false, unread: 1, refresh: vi.fn() }}><WorkspaceNotices /></InterviewInboxContext.Provider>
  </ApplicationUpdatesContext.Provider>)
  expect(screen.getByText('Tienes 1 entrevista con cambios sin leer.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Revisar entrevistas' })).toBeInTheDocument()
})
