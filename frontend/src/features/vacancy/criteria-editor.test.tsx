import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, it } from 'vitest'
import { CriteriaEditor } from './criteria-editor'
import { MAX_CRITERIA, newCriterion, type CriterionDraft } from './criterion-draft'

function Editor({ initial = [newCriterion()], disabled = false }: { initial?: CriterionDraft[]; disabled?: boolean }) {
  const [value, setValue] = useState(initial)
  return <CriteriaEditor value={value} onChange={setValue} disabled={disabled} />
}

it('añade una plantilla solo al solicitarla y reemplaza únicamente el criterio vacío inicial', async () => {
  const user = userEvent.setup()
  render(<Editor />)
  expect(screen.getByLabelText('Nombre corto')).toHaveValue('')
  await user.click(screen.getByRole('button', { name: 'Añadir plantilla' }))
  expect(screen.getAllByRole('article')).toHaveLength(1)
  expect(screen.getByLabelText('Nombre corto')).toHaveValue('Experiencia con Java')
  expect(screen.getByLabelText('Pregunta')).toHaveValue('¿El CV declara experiencia profesional con Java?')
  await user.type(screen.getByLabelText('Nombre corto'), ' y Kotlin')
  await user.selectOptions(screen.getByLabelText('Plantilla de criterio'), 'language')
  await user.click(screen.getByRole('button', { name: 'Añadir plantilla' }))
  expect(screen.getAllByRole('article')).toHaveLength(2)
  expect(within(screen.getByRole('article', { name: 'Criterio 1' })).getByLabelText('Nombre corto')).toHaveValue('Experiencia con Java y Kotlin')
})

it('reordena respuestas y refleja los puntos de cada posición en la configuración', async () => {
  const user = userEvent.setup()
  render(<Editor initial={[newCriterion({label:'Experiencia',question:'¿Qué experiencia declara?',kind:'CHOICE',options:['Alta','Media','Baja']})]} />)
  await user.click(screen.getByRole('button', { name: 'Subir opción 2 del criterio 1' }))
  expect(screen.getByLabelText('Opción 1 del criterio 1')).toHaveValue('Media')
  expect(screen.getByLabelText('Opción 2 del criterio 1')).toHaveValue('Alta')
  expect(screen.getByRole('button', { name: 'Subir opción 1 del criterio 1' })).toBeDisabled()
  await user.click(screen.getByText('Ver configuración de evaluación'))
  expect(screen.getByText('Media: 100 puntos')).toBeVisible()
  expect(screen.getByText('Alta: 50 puntos')).toBeVisible()
  expect(screen.getByText('Baja: 0 puntos')).toBeVisible()
})

it('conserva las opciones al alternar el tipo y explica el significado de sí/no', async () => {
  const user = userEvent.setup()
  render(<Editor initial={[newCriterion({kind:'CHOICE',options:['Avanzado','Básico']})]} />)
  await user.click(screen.getByRole('radio', { name: 'Sí / No' }))
  expect(screen.getByText(/Redacta la pregunta en positivo/)).toBeVisible()
  await user.click(screen.getByRole('radio', { name: 'Varias opciones' }))
  expect(screen.getByLabelText('Opción 1 del criterio 1')).toHaveValue('Avanzado')
  expect(screen.getByLabelText('Opción 2 del criterio 1')).toHaveValue('Básico')
})

it('respeta el límite de criterios al añadir plantillas', () => {
  render(<Editor initial={Array.from({length:MAX_CRITERIA},()=>newCriterion({label:'Java',question:'¿Declara Java?'}))} />)
  expect(screen.getByRole('button', { name: 'Añadir plantilla' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Agregar criterio' })).toBeDisabled()
})

it('bloquea plantillas y el orden de las respuestas mientras se guarda', () => {
  render(<Editor disabled initial={[newCriterion({kind:'CHOICE',options:['Alta','Baja']})]} />)
  expect(screen.getByLabelText('Plantilla de criterio')).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Añadir plantilla' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Bajar opción 1 del criterio 1' })).toBeDisabled()
  expect(screen.getByRole('radio', { name: 'Sí / No' })).toBeDisabled()
})
