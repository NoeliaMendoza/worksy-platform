import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { CandidateRow } from '@/components/candidate-row'
import { ConfidenceBadge } from '@/components/confidence-badge'
import { DecisionControl } from '@/components/decision-control'
import { EvidenceStrip } from '@/components/evidence-strip'
import { FilterBar } from '@/components/filter-bar'
import { ScoreMeter } from '@/components/score-meter'
import { Button } from '@/components/ui/button'
import type { Decision } from '@/features/ranking/types'
import { vacancyRankingMock } from '@/mocks/vacancy-ranking'

const { vacancy, candidates } = vacancyRankingMock
const first = candidates[0]!

describe('ConfidenceBadge', () => {
  it.each([
    ['alta', 'Confianza alta'],
    ['media', 'Confianza media'],
    ['baja', 'Confianza baja'],
  ] as const)('expresa la confianza %s con palabras', (level, text) => {
    render(<ConfidenceBadge level={level} />)
    expect(screen.getByText(text)).toBeInTheDocument()
  })
})

describe('ScoreMeter', () => {
  it('muestra el valor y lo expone como medidor accesible', () => {
    render(<ScoreMeter value={86.4} label="Compatibilidad de prueba" />)
    const meter = screen.getByRole('meter', { name: 'Compatibilidad de prueba' })
    expect(meter).toHaveAttribute('aria-valuenow', '86')
    expect(screen.getByText('86')).toBeInTheDocument()
  })

  it('limita el valor entre 0 y 100', () => {
    render(<ScoreMeter value={140} label="Fuera de rango" />)
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '100')
  })
})

describe('DecisionControl', () => {
  function Harness({ onChange }: { onChange?: (d: Decision | null) => void }) {
    const [value, setValue] = useState<Decision | null>(null)
    return (
      <DecisionControl
        value={value}
        candidateName="Persona sintética"
        onChange={(d) => {
          setValue(d)
          onChange?.(d)
        }}
      />
    )
  }

  it('ofrece las tres decisiones humanas', () => {
    render(<Harness />)
    const group = screen.getByRole('group', { name: 'Decisión sobre Persona sintética' })
    for (const name of ['Preseleccionar', 'En espera', 'Descartar']) {
      expect(group).toContainElement(screen.getByRole('button', { name }))
    }
  })

  it('marca la opción elegida y la desmarca al pulsarla otra vez', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const pre = screen.getByRole('button', { name: 'Preseleccionar' })

    await user.click(pre)
    expect(pre).toHaveAttribute('aria-pressed', 'true')
    expect(onChange).toHaveBeenLastCalledWith('preseleccionado')

    await user.click(screen.getByRole('button', { name: 'Descartar' }))
    expect(pre).toHaveAttribute('aria-pressed', 'false')

    await user.click(screen.getByRole('button', { name: 'Descartar' }))
    expect(onChange).toHaveBeenLastCalledWith(null)
  })
})

describe('EvidenceStrip', () => {
  it('anuncia el resultado de cada criterio con texto, no solo con color', () => {
    render(<EvidenceStrip criteria={vacancy.criteria} answers={candidates[3]!.answers} />)
    expect(screen.getAllByRole('listitem')).toHaveLength(vacancy.criteria.length)
    expect(screen.getByText(/PostgreSQL/).closest('li')).toHaveTextContent('No cumple')
  })

  it('muestra la cita del CV al expandir y avisa si no hay evidencia', () => {
    render(<EvidenceStrip criteria={vacancy.criteria} answers={candidates[4]!.answers} expanded />)
    expect(screen.getByText(/módulo de citas con Spring Boot/)).toBeInTheDocument()
    expect(screen.getByText('El CV no menciona este criterio.')).toBeInTheDocument()
  })
})

describe('FilterBar', () => {
  it('muestra contadores y notifica el filtro elegido', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(
      <FilterBar
        label="Filtrar"
        value="a"
        onChange={onChange}
        options={[
          { value: 'a', label: 'Todos', count: 8 },
          { value: 'b', label: 'Sin revisar', count: 5 },
        ]}
      />,
    )
    expect(screen.getByRole('button', { name: /^Todos\s*,\s*8$/ })).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: /^Sin revisar\s*,\s*5$/ }))
    expect(onChange).toHaveBeenCalledWith('b')
  })
})

describe('Button', () => {
  it('es type="button" por defecto para no enviar formularios por accidente', () => {
    render(<Button>Guardar</Button>)
    expect(screen.getByRole('button', { name: 'Guardar' })).toHaveAttribute('type', 'button')
  })
})

describe('CandidateRow', () => {
  it('muestra puesto, compatibilidad, confianza y la decisión siempre visible', () => {
    render(<CandidateRow candidate={first} criteria={vacancy.criteria} onDecide={() => {}} />)
    const row = screen.getByRole('article', { name: /Valeria Quispe Romero/ })
    expect(row).toHaveTextContent('91')
    expect(row).toHaveTextContent('Confianza alta')
    expect(row).toHaveTextContent('Puesto 1')
    expect(screen.getByRole('button', { name: 'Preseleccionar' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('despliega la evidencia del CV', async () => {
    const user = userEvent.setup()
    render(<CandidateRow candidate={first} criteria={vacancy.criteria} onDecide={() => {}} />)
    const toggle = screen.getByRole('button', { name: 'Ver evidencia' })
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText(/RabbitMQ y reintentos/)).toBeInTheDocument()
  })

  it('envía la decisión con el id del candidato', async () => {
    const user = userEvent.setup()
    const onDecide = vi.fn()
    render(<CandidateRow candidate={candidates[1]!} criteria={vacancy.criteria} onDecide={onDecide} />)
    await user.click(screen.getByRole('button', { name: 'En espera' }))
    expect(onDecide).toHaveBeenCalledWith('c-102', 'en_espera')
  })
})

describe('Logo', () => {
  it('muestra el nombre con mayúscula y deja el símbolo como decorativo', async () => {
    const { Logo } = await import('@/components/logo')
    const { container } = render(<Logo />)
    expect(screen.getByText('Worksy')).toBeInTheDocument()
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })
})

describe('CriteriaSummary', () => {
  it('resume los criterios con texto', async () => {
    const { CriteriaSummary } = await import('@/components/criteria-summary')
    render(<CriteriaSummary answers={candidates[3]!.answers} />)
    expect(screen.getByText('Cumple 4 de 5 criterios')).toBeInTheDocument()
    expect(screen.getByText(/1 sin cumplir/)).toBeInTheDocument()
  })
})

describe('AppShell', () => {
  it('abre el menú móvil y lo cierra con Escape devolviendo el foco al botón', async () => {
    const { AppShell } = await import('@/components/app-shell')
    const user = userEvent.setup()
    render(
      <AppShell nav={[]} current="vacantes">
        <p>Contenido</p>
      </AppShell>,
    )
    const toggle = screen.getByRole('button', { name: 'Abrir menú' })
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
  })
})
