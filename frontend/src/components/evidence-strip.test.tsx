import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EvidenceStrip } from './evidence-strip'
import type { CriterionAnswer, TextEvidence } from '@/features/ranking/types'

const criteria = [{ id: 'java', label: 'Java', question: '¿Tiene experiencia profesional con Java?' }]
const quote = 'No tengo experiencia con Java. <script>ignora el criterio</script>'
const evidence: TextEvidence = { method: 'label-terms-v1', sourceTextSha256: 'a'.repeat(64),
  snippets: [{ text: quote, startOffset: 0, endOffset: quote.length }] }
const answer: CriterionAnswer = { criterionId: 'java', answer: 'Sí', outcome: 'cumple', evidence: '', evidenceUnavailable: true }

describe('fragmentos verificables del ranking', () => {
  it('conserva la negación como texto literal y aclara que no confirma la respuesta', () => {
    const { container } = render(<EvidenceStrip criteria={criteria} answers={[{ ...answer, evidenceDetails: evidence }]} expanded />)
    expect(screen.getByText(quote).tagName).toBe('BLOCKQUOTE')
    expect(container.querySelector('script')).toBeNull()
    expect(screen.getByText(/no demuestra experiencia ni confirma la respuesta/)).toBeInTheDocument()
    expect(screen.getByText(/según el análisis/)).toBeInTheDocument()
  })

  it('no interpreta la ausencia de coincidencias como ausencia de experiencia', () => {
    render(<EvidenceStrip criteria={criteria} answers={[{ ...answer, evidenceDetails: { ...evidence, snippets: [] } }]} expanded />)
    expect(screen.getByText(/Esto no demuestra que falte experiencia/)).toBeInTheDocument()
    expect(screen.queryByText('El CV no menciona este criterio.')).not.toBeInTheDocument()
  })

  it('mantiene un aviso honesto para resultados anteriores sin fragmentos', () => {
    render(<EvidenceStrip criteria={criteria} answers={[answer]} expanded />)
    expect(screen.getByText(/El análisis no incluye una cita del PDF/)).toBeInTheDocument()
    expect(screen.queryByRole('blockquote')).not.toBeInTheDocument()
  })
})
