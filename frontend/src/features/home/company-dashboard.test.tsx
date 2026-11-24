import { act, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context'
import { companyVacancies, type CompanyVacancy } from '@/features/vacancy/api'
import { interviewsApi, type InterviewItem } from '@/features/interviews/api'
import { messagesApi } from '@/features/messages/api'
import { RealtimeContext, type NoticeListener, type RealtimeValue } from '@/lib/realtime-context'
import { CompanyDashboard } from './company-dashboard'

const vacancy: CompanyVacancy = {id:'v-1',title:'Backend Java',description:'Descripción sintética de la oferta.',location:'Quito',modality:'HIBRIDO',status:'PUBLISHED',criteria:[],createdAt:'2026-11-18T12:00:00Z',updatedAt:'2026-11-18T12:00:00Z',publishedAt:'2026-11-18T12:00:00Z',closedAt:null}
const interview: InterviewItem = {interview:{id:'i-1',applicationId:'a-1',version:1,title:'Entrevista técnica',startsAt:'2099-04-10T15:00:00Z',durationMinutes:30,zoneId:'America/Guayaquil',mode:'ONLINE',place:'https://example.test/sala',note:null,status:'INVITED',updatedAt:'2026-11-18T12:00:00Z'},companyName:'Empresa sintética',vacancyTitle:vacancy.title,candidateEmail:'persona@example.test',unread:true}
function view(role:'COMPANY_ADMIN'|'RECRUITER'|'CANDIDATE'='RECRUITER', live:RealtimeValue|null=null) {
  const auth:AuthContextValue={state:{status:'authenticated',accessToken:'synthetic',expiresAt:9999999999999,user:{id:'u-1',email:'demo@example.test',role,companyId:'c-1',createdAt:''}},login:vi.fn(),logout:vi.fn()}
  return render(<AuthContext.Provider value={auth}><RealtimeContext.Provider value={live}><CompanyDashboard/></RealtimeContext.Provider></AuthContext.Provider>)
}
beforeEach(()=>{
  vi.spyOn(companyVacancies,'list').mockResolvedValue([vacancy,{...vacancy,id:'v-2',title:'Borrador reciente',status:'DRAFT',updatedAt:'2026-11-20T12:00:00Z'}])
  vi.spyOn(interviewsApi,'list').mockResolvedValue({items:[interview],hasMore:true})
  vi.spyOn(messagesApi,'inbox').mockResolvedValue([{applicationId:'a-1',vacancyTitle:'Backend Java',companyName:'Empresa sintética',candidateEmail:null,lastMessage:'Mensaje privado',lastSentAt:'2026-11-18T12:00:00Z',lastFromCandidate:true,unread:2}])
})
afterEach(()=>vi.restoreAllMocks())

it('muestra contadores reales, orden reciente y accesos a los procesos',async()=>{
  view()
  await screen.findByText('Borrador reciente')
  expect(within(screen.getByRole('region',{name:'Vacantes publicadas'})).getByText('1')).toBeInTheDocument()
  expect(within(screen.getByRole('region',{name:'Borradores'})).getByText('1')).toBeInTheDocument()
  await waitFor(()=>expect(within(screen.getByRole('region',{name:'Mensajes sin leer'})).getByText('2')).toBeInTheDocument())
  const rows=within(screen.getByRole('list',{name:'Vacantes recientes'})).getAllByRole('listitem')
  expect(rows[0]).toHaveTextContent('Borrador reciente')
  expect(within(rows[0]!).getByRole('link')).toHaveAttribute('href','/vacantes/v-2')
  expect(screen.getByRole('link',{name:'Nueva vacante'})).toHaveAttribute('href','/vacantes/nueva')
})
it('mantiene la agenda cuando falla la consulta de vacantes y no inventa ceros',async()=>{
  vi.mocked(companyVacancies.list).mockRejectedValue(new Error('offline'))
  view()
  await screen.findByText('Entrevista técnica')
  expect(await screen.findByText('No se pudieron cargar las vacantes')).toBeInTheDocument()
  expect(within(screen.getByRole('region',{name:'Vacantes publicadas'})).getByText('—')).toBeInTheDocument()
  expect(screen.getByRole('button',{name:'Reintentar'})).toBeInTheDocument()
})
it('informa del alcance de la agenda y excluye entrevistas canceladas o pasadas',async()=>{
  vi.mocked(interviewsApi.list).mockResolvedValue({items:[interview,{...interview,interview:{...interview.interview,id:'i-2',title:'Cancelada',status:'CANCELLED'}},{...interview,interview:{...interview.interview,id:'i-3',title:'Anterior',startsAt:'2020-01-01T12:00:00Z'}}],hasMore:true})
  view()
  expect(await screen.findByText(/Hay más resultados en la agenda/)).toBeInTheDocument()
  expect(screen.getByText(/GMT-5/)).toBeInTheDocument()
  expect(screen.queryByText('Cancelada')).not.toBeInTheDocument()
  expect(screen.queryByText('Anterior')).not.toBeInTheDocument()
})
it('no marca mensajes ni cambios como leídos al mostrar el resumen',async()=>{
  const readMessage=vi.spyOn(messagesApi,'markRead')
  const readInterview=vi.spyOn(interviewsApi,'read')
  view('COMPANY_ADMIN')
  expect(await screen.findByRole('link',{name:'Persona candidata'})).toHaveAttribute('href','/mensajes/a-1')
  expect(screen.queryByText('Mensaje privado')).not.toBeInTheDocument()
  expect(readMessage).not.toHaveBeenCalled()
  expect(readInterview).not.toHaveBeenCalled()
})
it('recupera el resumen al reconectar sin un botón permanente de actualización',async()=>{
  const listeners=new Set<NoticeListener>()
  const live:RealtimeValue={status:'connecting',listen:(fn)=>{listeners.add(fn);return()=>{listeners.delete(fn)}},emit:(notice)=>listeners.forEach(fn=>fn(notice))}
  view('RECRUITER',live)
  await screen.findByText('Borrador reciente')
  vi.mocked(companyVacancies.list).mockResolvedValue([])
  act(()=>live.emit({type:'reconnected'}))
  expect(await screen.findByText('Comienza con tu primera vacante')).toBeInTheDocument()
  expect(screen.queryByRole('button',{name:/Actualizar/})).not.toBeInTheDocument()
})
it('no consulta datos de empresa para una persona candidata',()=>{
  view('CANDIDATE')
  expect(companyVacancies.list).not.toHaveBeenCalled()
  expect(interviewsApi.list).not.toHaveBeenCalled()
  expect(messagesApi.inbox).not.toHaveBeenCalled()
})
