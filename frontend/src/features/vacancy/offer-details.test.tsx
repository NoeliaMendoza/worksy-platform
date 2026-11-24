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

it('no publica criterios incompletos y enlaza los errores con sus campos', async () => {
  const update = vi.spyOn(companyVacancies, 'update')
  const user = userEvent.setup()
  render(<VacancyEditor token="synthetic" initial={{...draft, criteria:[{...draft.criteria[0]!,label:'',question:''}]}} onSaved={vi.fn()} />)
  expect(screen.getByText(/Falta completar o corregir: criterio 1/)).toBeInTheDocument()
  await user.click(screen.getByRole('button',{name:'Guardar y publicar'}))
  await waitFor(()=>expect(screen.getByLabelText('Nombre corto')).toHaveFocus())
  expect(screen.getByLabelText('Nombre corto')).toHaveAccessibleDescription(/entre 2 y 60/)
  expect(screen.getByLabelText('Pregunta')).toHaveAccessibleDescription(/entre 5 y 2000/)
  expect(update).not.toHaveBeenCalled()
})

it('detecta opciones repetidas y elimina el error cuando se corrigen', async () => {
  const update = vi.spyOn(companyVacancies, 'update')
  render(<VacancyEditor token="synthetic" initial={{...draft,criteria:[{...draft.criteria[0]!,kind:'CHOICE',options:['Java',' java ']}]}} onSaved={vi.fn()} />)
  await userEvent.click(screen.getByRole('button',{name:'Guardar y publicar'}))
  await waitFor(()=>expect(screen.getByLabelText('Opción 1 del criterio 1')).toHaveFocus())
  expect(screen.getByText('Las opciones no pueden repetirse.')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Opción 2 del criterio 1'),{target:{value:'Python'}})
  expect(screen.queryByText('Las opciones no pueden repetirse.')).not.toBeInTheDocument()
  expect(update).not.toHaveBeenCalled()
})

it('conserva el borrador creado si falla la publicación y lo reutiliza al reintentar', async () => {
  const create = vi.spyOn(companyVacancies,'create').mockResolvedValue(draft)
  const update = vi.spyOn(companyVacancies,'update').mockResolvedValue(draft)
  const publish = vi.spyOn(companyVacancies,'publish').mockRejectedValueOnce(new Error('No se pudo publicar.')).mockResolvedValue({...draft,status:'PUBLISHED'})
  const saved = vi.fn()
  render(<VacancyEditor token="synthetic" onSaved={saved} />)
  fireEvent.change(screen.getByLabelText('Título'),{target:{value:draft.title}})
  fireEvent.change(screen.getByLabelText('Ubicación'),{target:{value:draft.location}})
  fireEvent.change(screen.getByLabelText('Descripción'),{target:{value:draft.description}})
  fireEvent.change(screen.getByLabelText('Tipo de contrato *'),{target:{value:'INDEFINITE'}})
  fireEvent.change(screen.getByLabelText('Jornada *'),{target:{value:'FULL_TIME'}})
  fireEvent.change(screen.getByLabelText('Responsabilidades *'),{target:{value:'Desarrollar servicios.'}})
  fireEvent.change(screen.getByLabelText('Requisitos indispensables *'),{target:{value:'Java'}})
  fireEvent.change(screen.getByLabelText('Nombre corto'),{target:{value:'Java'}})
  fireEvent.change(screen.getByLabelText('Pregunta'),{target:{value:'¿Tiene experiencia con Java?'}})
  await userEvent.click(screen.getByRole('button',{name:'Guardar y publicar'}))
  expect(await screen.findByRole('alert')).toHaveTextContent(/No se pudo guardar la vacante/)
  await userEvent.click(screen.getByRole('button',{name:'Guardar y publicar'}))
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce())
  expect(create).toHaveBeenCalledOnce()
  expect(update).toHaveBeenCalledExactlyOnceWith('synthetic','v-test',expect.any(Object))
  expect(publish).toHaveBeenCalledTimes(2)
})
