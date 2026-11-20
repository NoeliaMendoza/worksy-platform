import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { companyVacancies, type CompanyVacancy, type OfferDetails } from './api'
import { VacancyEditor } from './vacancy-editor'
import { OfferContent } from './offer-details'
import { emptyOffer, offerErrors, offerDraft } from './offer-draft'

const offer: OfferDetails = { contractType: 'INDEFINITE', workSchedule: 'FULL_TIME', area: 'Tecnología', experienceLevel: 'Intermedio', responsibilities: 'Desarrollar servicios.', requirements: 'Experiencia con Java.', desirable: null, benefits: 'Formación continua', schedule: 'Lunes a viernes', remoteConditions: 'Residencia en Ecuador', salaryMin: 1200, salaryMax: 2000, currency: 'USD', salaryPeriod: 'MONTH' }
const draft: CompanyVacancy = { id:'v-test', title:'Backend Java', description:'Descripción sintética suficientemente larga.', location:'Quito', modality:'REMOTO', status:'DRAFT', criteria:[{key:'q1',label:'Java',question:'Pregunta interna',kind:'YES_NO',options:['Sí','No']}], createdAt:'2026-11-18T12:00:00Z',updatedAt:'2026-11-18T12:00:00Z',publishedAt:null,closedAt:null,offer }
afterEach(() => vi.restoreAllMocks())

it('valida salario completo, orden, precisión y moneda, y admite salario omitido', () => {
  expect(offerErrors(emptyOffer, false)).toEqual({})
  expect(offerErrors(offerDraft(offer), true)).toEqual({})
  expect(offerErrors({...offerDraft(offer),salaryMax:'1000'},true).salaryMin).toBeTruthy()
  expect(offerErrors({...offerDraft(offer),salaryMin:'1.001'},true).salaryMin).toBeTruthy()
  expect(offerErrors({...emptyOffer,salaryMin:'1200'},false)).toHaveProperty('salaryPeriod')
  expect(offerErrors({...offerDraft(offer),currency:'ZZZ'},true).currency).toBeTruthy()
})

it('permite guardar borrador incompleto y bloquea su publicación con foco en el campo', async () => {
  const update=vi.spyOn(companyVacancies,'update').mockResolvedValue(draft)
  const publish=vi.spyOn(companyVacancies,'publish')
  const user=userEvent.setup()
  render(<VacancyEditor token="synthetic" initial={{...draft,offer:null}} onSaved={vi.fn()} />)
  await user.click(screen.getByRole('button',{name:'Guardar y publicar'}))
  expect(screen.getByLabelText('Tipo de contrato *')).toHaveFocus()
  expect(update).not.toHaveBeenCalled()
  expect(publish).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button',{name:'Guardar borrador'}))
  await waitFor(()=>expect(update).toHaveBeenCalledOnce())
  expect(update.mock.calls[0]![2].offer?.contractType).toBeNull()
})

it('la vista previa muestra los datos públicos sin escribir ni incluir preguntas internas', async () => {
  const update=vi.spyOn(companyVacancies,'update')
  const user=userEvent.setup()
  render(<VacancyEditor token="synthetic" initial={draft} onSaved={vi.fn()} />)
  await user.click(screen.getByRole('button',{name:'Vista previa de la oferta'}))
  const preview=within(screen.getByRole('region',{name:'Vista previa de la oferta'}))
  expect(preview.getByText('Indefinido')).toBeInTheDocument()
  expect(preview.getByText('Desarrollar servicios.')).toBeInTheDocument()
  expect(preview.getByText(/USD · Mensual/)).toBeInTheDocument()
  expect(preview.queryByText('Pregunta interna')).not.toBeInTheDocument()
  expect(update).not.toHaveBeenCalled()
})

it('impide guardar un rango salarial invertido', async () => {
  const update=vi.spyOn(companyVacancies,'update')
  render(<VacancyEditor token="synthetic" initial={draft} onSaved={vi.fn()} />)
  fireEvent.change(screen.getByLabelText('Salario máximo'),{target:{value:'1000'}})
  await userEvent.click(screen.getByRole('button',{name:'Guardar borrador'}))
  expect(screen.getByLabelText('Salario mínimo')).toHaveFocus()
  expect(update).not.toHaveBeenCalled()
})

it('muestra las condiciones públicas sin inventar beneficios ausentes', () => {
  render(<OfferContent offer={{...offer,benefits:null}} />)
  expect(screen.getByText('Tiempo completo')).toBeInTheDocument()
  expect(screen.getByText('Residencia en Ecuador')).toBeInTheDocument()
  expect(screen.queryByText('Beneficios')).not.toBeInTheDocument()
})
