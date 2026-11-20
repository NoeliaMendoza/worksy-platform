import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ApplicationJourney } from './application-journey'
import type { MyApplication } from './api'

const base: MyApplication = { id: 'a-sintetica', vacancyId: 'v-sintetica', vacancyTitle: 'Backend', companyName: 'Empresa sintética',
  vacancyStatus: 'PUBLISHED', cvId: 'cv-1', appliedAt: '2026-11-12T15:00:00Z', stage: 'INTERVIEW', milestones: [
    { stage: 'RECEIVED', changedAt: '2026-11-12T15:00:00Z' },
    { stage: 'REVIEW', changedAt: '2026-11-13T15:00:00Z' },
    { stage: 'INTERVIEW', changedAt: '2026-11-16T15:00:00Z' },
  ] }

describe('recorrido de una postulación', () => {
  it('muestra fechas registradas y distingue etapas futuras de la actual', () => {
    render(<ApplicationJourney application={base} />)
    const list = screen.getByRole('list', { name: 'Avance de Backend' })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(5)
    expect(items[2]).toHaveAttribute('aria-current', 'step')
    expect(items[2]!.querySelector('time')).toHaveAttribute('dateTime', '2026-11-16T15:00:00Z')
    expect(within(items[3]!).getByText('Pendiente')).toBeInTheDocument()
    expect(items[3]!.querySelector('time')).toBeNull()
    expect(items[4]!.querySelector('time')).toBeNull()
  })

  it('un rechazo temprano cierra el recorrido sin mostrar entrevista ni oferta', () => {
    render(<ApplicationJourney application={{ ...base, stage: 'REJECTED', milestones: [base.milestones![0]!, { stage: 'REJECTED', changedAt: '2026-11-13T15:00:00Z' }] }} />)
    const list = screen.getByRole('list')
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(within(list).queryByText('Entrevista')).not.toBeInTheDocument()
    expect(within(list).queryByText('Oferta')).not.toBeInTheDocument()
    expect(within(list).getByText(/No continúa/).closest('li')).toHaveAttribute('aria-current', 'step')
  })

  it('un rechazo posterior conserva únicamente el recorrido registrado', () => {
    render(<ApplicationJourney application={{ ...base, stage: 'REJECTED', milestones: [...base.milestones!, { stage: 'REJECTED', changedAt: '2026-11-17T15:00:00Z' }] }} />)
    expect(screen.getAllByRole('listitem')).toHaveLength(4)
    expect(screen.getByText('Entrevista')).toBeInTheDocument()
    expect(screen.queryByText('Oferta')).not.toBeInTheDocument()
    expect(screen.queryByText('Contratada')).not.toBeInTheDocument()
  })

  it('con una API anterior no inventa fechas para las etapas intermedias', () => {
    render(<ApplicationJourney application={{ ...base, milestones: undefined }} />)
    const items = screen.getAllByRole('listitem')
    expect(items[0]!.querySelector('time')).toHaveAttribute('dateTime', base.appliedAt)
    expect(within(items[1]!).getByText('Fecha sin registro')).toBeInTheDocument()
    expect(items[1]!.querySelector('time')).toBeNull()
    expect(items[2]).toHaveAttribute('aria-current', 'step')
  })
})
