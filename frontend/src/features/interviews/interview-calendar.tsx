import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { useResource } from '@/lib/use-resource'
import { interviewsApi, type InterviewItem } from './api'
import { interviewState, type Marker } from './interview-labels'
import { clock, dayKey, viewerZone } from './interview-time'

export type CalendarMode = 'semana' | 'mes'

/** VALOR SUPUESTO: la semana muestra de 7:00 a 21:00; las citas fuera de ese horario se ven arriba o abajo. */
const FIRST_HOUR = 7
const LAST_HOUR = 21
const HOUR_PX = 56
const WEEKDAYS = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom']

const chip: Record<Marker, string> = {
  confirmed: 'border border-azure-200 bg-azure-200',
  pending: 'border border-dashed border-mid-700 bg-white',
  change: 'border border-azure-600 bg-white',
  closed: 'border border-ink-100 bg-ink-100',
  cancelled: 'border border-ink-100 bg-surface-page line-through',
}

/** Lunes 00:00 local de la semana que contiene `date`. */
function startOfWeek(date: Date): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
}

function addDays(date: Date, days: number) {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

/** Primer día visible y cantidad de días para la vista elegida (el mes ocupa 6 semanas: máximo de la API). */
function calendarRange(mode: CalendarMode, anchor: Date): { from: Date; days: number } {
  if (mode === 'semana') return { from: startOfWeek(anchor), days: 7 }
  return { from: startOfWeek(new Date(anchor.getFullYear(), anchor.getMonth(), 1)), days: 42 }
}

export function InterviewCalendar({ token, company, mode, anchor, mine, onAnchor, onSelect }: {
  token: string; company: boolean; mode: CalendarMode; anchor: Date; mine: boolean
  onAnchor: (date: Date) => void; onSelect: (id: string) => void
}) {
  const { from, days } = calendarRange(mode, anchor)
  const to = addDays(from, days)
  const { state, retry } = useResource(() => interviewsApi.range(token, from.toISOString(), to.toISOString(), mine),
    `${token}:${mode}:${from.toISOString()}:${mine}`, 'No se pudo cargar el calendario.')
  const items = state.status === 'ready' ? state.data.items : []
  const byDay = new Map<string, InterviewItem[]>()
  for (const item of items) {
    const key = dayKey(item.interview.startsAt)
    byDay.set(key, [...(byDay.get(key) ?? []), item])
  }
  const today = dayKey(new Date())
  const title = mode === 'semana'
    ? `Del ${from.getDate()} al ${addDays(from, 6).toLocaleDateString('es', { day: 'numeric', month: 'long', year: 'numeric' })}`
    : anchor.toLocaleDateString('es', { month: 'long', year: 'numeric' }).replace(/^./, (c) => c.toUpperCase())
  const step = (direction: number) => onAnchor(mode === 'semana' ? addDays(anchor, 7 * direction)
    : new Date(anchor.getFullYear(), anchor.getMonth() + direction, 1))

  return <section aria-label="Calendario de entrevistas" className="flex min-w-0 flex-col">
    <div className="flex flex-wrap items-center gap-2 border-b border-ink-100 py-2">
      <Button variant="secondary" size="sm" className="min-h-11 px-4" onClick={() => onAnchor(new Date())}>Hoy</Button>
      <Button variant="ghost" size="icon" aria-label={mode === 'semana' ? 'Semana anterior' : 'Mes anterior'} onClick={() => step(-1)}><ChevronLeft aria-hidden="true" /></Button>
      <Button variant="ghost" size="icon" aria-label={mode === 'semana' ? 'Semana siguiente' : 'Mes siguiente'} onClick={() => step(1)}><ChevronRight aria-hidden="true" /></Button>
      <h2 className="text-base font-semibold text-navy-900" aria-live="polite">{title}</h2>
      <ul className="ml-auto flex flex-wrap gap-x-5 gap-y-1 text-[0.8125rem] text-ink-700" aria-label="Leyenda">
        <li className="flex items-center gap-2"><span aria-hidden="true" className="size-3.5 rounded bg-azure-200" />Confirmada</li>
        <li className="flex items-center gap-2"><span aria-hidden="true" className="size-3 rounded border border-dashed border-mid-700" />Por confirmar</li>
        <li className="flex items-center gap-2"><span aria-hidden="true" className="size-3 rounded border border-azure-600" />Cambio solicitado</li>
      </ul>
    </div>
    {state.status === 'error' && <p role="alert" className="py-4 text-sm text-ink-700">{state.message} <button className="cursor-pointer underline" onClick={retry}>Intentar de nuevo</button></p>}
    {state.status === 'loading' && <p role="status" className="sr-only">Cargando el calendario…</p>}
    <div className="overflow-x-auto">
      {mode === 'semana' ? <Week from={from} byDay={byDay} today={today} company={company} onSelect={onSelect} />
        : <Month from={from} month={anchor.getMonth()} byDay={byDay} today={today} company={company} onSelect={onSelect} />}
    </div>
    <p className="pt-3 text-xs text-ink-500">Horas en tu zona: {viewerZone()}.</p>
  </section>
}

function Week({ from, byDay, today, company, onSelect }: {
  from: Date; byDay: Map<string, InterviewItem[]>; today: string; company: boolean; onSelect: (id: string) => void
}) {
  const hours = Array.from({ length: LAST_HOUR - FIRST_HOUR }, (_, n) => FIRST_HOUR + n)
  return <div className="min-w-[48rem]">
    <div className="grid grid-cols-[3.5rem_repeat(7,minmax(0,1fr))] border-b border-ink-100">
      <span />
      {Array.from({ length: 7 }, (_, n) => {
        const day = addDays(from, n)
        const isToday = dayKey(day) === today
        return <div key={n} className="flex items-center gap-2 px-2 py-2.5">
          <span className="text-[0.8125rem] text-ink-500">{WEEKDAYS[n]}</span>
          <span className={cn('grid size-8 place-items-center rounded-full text-base font-bold tabular-nums', isToday ? 'bg-navy-900 text-white' : 'text-navy-900')}>{day.getDate()}</span>
        </div>
      })}
    </div>
    <div className="grid grid-cols-[3.5rem_repeat(7,minmax(0,1fr))]">
      <div aria-hidden="true">{hours.map((h) => <div key={h} style={{ height: HOUR_PX }} className="-translate-y-2 text-xs text-ink-500 tabular-nums">{String(h).padStart(2, '0')}:00</div>)}</div>
      {Array.from({ length: 7 }, (_, n) => {
        const day = addDays(from, n)
        const items = byDay.get(dayKey(day)) ?? []
        return <div key={n} className={cn('relative border-l border-ink-100', n > 4 && 'bg-surface-page')}
          style={{ height: HOUR_PX * hours.length, backgroundImage: 'linear-gradient(var(--color-ink-100) 1px, transparent 1px)', backgroundSize: `100% ${HOUR_PX}px` }}>
          {items.map((item) => {
            const start = new Date(item.interview.startsAt)
            const minutes = (start.getHours() - FIRST_HOUR) * 60 + start.getMinutes()
            const top = Math.min(Math.max(minutes, 0), (LAST_HOUR - FIRST_HOUR) * 60 - 30) * (HOUR_PX / 60)
            const height = Math.max(item.interview.durationMinutes * (HOUR_PX / 60), 46)
            const state = interviewState(item, company)
            return <button key={item.interview.id} type="button" onClick={() => onSelect(item.interview.id)} style={{ top, height }}
              aria-label={`${item.interview.title}, ${clock(item.interview.startsAt)}, ${state.label}`}
              className={cn('absolute inset-x-1 flex cursor-pointer flex-col overflow-hidden rounded-lg px-2 py-1.5 text-left text-xs leading-4 text-navy-900 focus-visible:outline-2 focus-visible:outline-azure-500', chip[state.marker])}>
              <span className="truncate"><strong className="font-semibold tabular-nums">{clock(item.interview.startsAt)}</strong> {item.interview.title}</span>
              <span className="truncate text-ink-700">{company ? item.candidateEmail : item.companyName}</span>
            </button>
          })}
        </div>
      })}
    </div>
  </div>
}

function Month({ from, month, byDay, today, company, onSelect }: {
  from: Date; month: number; byDay: Map<string, InterviewItem[]>; today: string; company: boolean; onSelect: (id: string) => void
}) {
  return <div className="grid min-w-[44rem] grid-cols-7">
    {WEEKDAYS.map((d) => <span key={d} className="px-2 py-2.5 text-[0.8125rem] text-ink-500">{d}</span>)}
    {Array.from({ length: 42 }, (_, n) => {
      const day = addDays(from, n)
      const inMonth = day.getMonth() === month
      const items = byDay.get(dayKey(day)) ?? []
      return <div key={n} className={cn('flex min-h-24 flex-col gap-1 border-t border-ink-100 p-1.5', !inMonth && 'bg-surface-page')}>
        <span className={cn('grid size-7 place-items-center rounded-full text-[0.8125rem] font-semibold tabular-nums',
          dayKey(day) === today ? 'bg-navy-900 text-white' : inMonth ? 'text-navy-900' : 'text-ink-500')}>{day.getDate()}</span>
        {items.slice(0, 3).map((item) => {
          const state = interviewState(item, company)
          return <button key={item.interview.id} type="button" onClick={() => onSelect(item.interview.id)}
            aria-label={`${item.interview.title}, ${clock(item.interview.startsAt)}, ${state.label}`}
            className={cn('cursor-pointer truncate rounded-md px-1.5 py-0.5 text-left text-xs text-navy-900 focus-visible:outline-2 focus-visible:outline-azure-500', chip[state.marker])}>
            {clock(item.interview.startsAt)} {item.interview.title}
          </button>
        })}
        {items.length > 3 && <span className="px-1.5 text-xs text-ink-500">y {items.length - 3} más</span>}
      </div>
    })}
  </div>
}
