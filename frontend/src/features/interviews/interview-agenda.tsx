import type { InterviewItem } from './api'
import { interviewState, markerClass } from './interview-labels'
import { dayHeading, dayKey, timeRange } from './interview-time'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/cn'

/**
 * Agenda agrupada por día en la zona de quien mira. La línea vertical une las citas del día; el punto de cada una
 * repite su estado (lleno = confirmada, anillo = por confirmar o con propuesta, gris = terminada).
 */
export function InterviewAgenda({ items, company, selected, onSelect }: {
  items: InterviewItem[]; company: boolean; selected: string | null; onSelect: (id: string) => void
}) {
  const days: { key: string; items: InterviewItem[] }[] = []
  for (const item of items) {
    const key = dayKey(item.interview.startsAt)
    const last = days.at(-1)
    if (last?.key === key) last.items.push(item)
    else days.push({ key, items: [item] })
  }
  return <div className="flex flex-col">
    {days.map((day) => {
      const heading = dayHeading(day.items[0]!.interview.startsAt)
      return <section key={day.key} aria-label={`${heading.weekday} ${heading.number}`} className="grid grid-cols-[3.75rem_minmax(0,1fr)] pt-5 sm:grid-cols-[4.75rem_minmax(0,1fr)]">
        <h3 className="flex flex-col font-semibold text-navy-900" aria-hidden="true">
          <span className="text-[1.75rem] leading-8 tracking-tight tabular-nums">{heading.number}</span>
          <span className="text-[0.8125rem] font-medium text-ink-500">{heading.weekday}</span>
        </h3>
        <ol className="border-l-2 border-azure-200">
          {day.items.map((item) => {
            const i = item.interview
            const state = interviewState(item, company)
            const active = selected === i.id
            return <li key={i.id} className="relative">
              <span aria-hidden="true" className={cn('absolute top-6 -left-[7px] size-3 rounded-full border-2', markerClass[state.marker])} />
              <button type="button" onClick={() => onSelect(i.id)} aria-current={active ? 'true' : undefined}
                className={cn('ml-3 flex w-[calc(100%-0.75rem)] cursor-pointer flex-wrap items-center gap-x-3 gap-y-1 rounded-lg p-3.5 text-left transition-colors hover:bg-azure-50 focus-visible:outline-2 focus-visible:outline-azure-500 sm:ml-4 sm:w-[calc(100%-1rem)]',
                  active && 'bg-azure-50')}>
                <span className="w-[5.75rem] shrink-0 text-sm font-semibold text-navy-900 tabular-nums">{timeRange(i.startsAt, i.durationMinutes)}</span>
                <span className="flex min-w-0 flex-[1_1_11rem] flex-col">
                  <span className={cn('flex items-center gap-2 text-[0.9375rem] font-semibold text-ink-900', i.status === 'CANCELLED' && 'line-through decoration-ink-300')}>
                    <span className="min-w-0 truncate">{i.title}</span>
                    {item.unread && <span className="size-2 shrink-0 rounded-full bg-azure-600" aria-label="con cambios sin leer" />}
                  </span>
                  <span className="truncate text-[0.8125rem] text-ink-500">{company ? `${item.candidateEmail}, ${item.vacancyTitle}` : `${item.companyName}, ${item.vacancyTitle}`}</span>
                </span>
                <Badge tone={state.tone}>{state.label}</Badge>
              </button>
            </li>
          })}
        </ol>
      </section>
    })}
  </div>
}
