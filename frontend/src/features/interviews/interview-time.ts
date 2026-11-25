/** La fecha del formulario usa la zona local del navegador, que se indica junto al control. */
export function localDateTime(instant: string): string {
  const date = new Date(instant)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function toInstant(local: string): string {
  const date = new Date(local)
  if (!local || !Number.isFinite(date.getTime()) || localDateTime(date.toISOString()) !== local) {
    throw new Error('La fecha u hora no es válida en la zona horaria indicada.')
  }
  return date.toISOString()
}

export function interviewTime(instant: string, zoneId: string): string {
  return new Intl.DateTimeFormat('es', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
    timeZone: zoneId, timeZoneName: 'shortOffset' }).format(new Date(instant))
}

/** Zona horaria de quien mira (la del navegador). */
export function viewerZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/** «Hora de Guayaquil (GMT-5)»: nombre legible de una zona y su desfase actual. */
export function zoneLabel(zoneId: string, at = new Date()): string {
  const city = zoneId.split('/').at(-1)?.replaceAll('_', ' ') ?? zoneId
  const offset = new Intl.DateTimeFormat('es', { timeZone: zoneId, timeZoneName: 'shortOffset' }).formatToParts(at)
    .find((p) => p.type === 'timeZoneName')?.value ?? ''
  return `Hora de ${city}${offset ? ` (${offset})` : ''}`
}

const parts = (instant: string | Date, zone: string, options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('es', { timeZone: zone, ...options }).format(typeof instant === 'string' ? new Date(instant) : instant)

/** Clave AAAA-MM-DD del día en la zona indicada (para agrupar por día). */
export function dayKey(instant: string | Date, zone = viewerZone()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(typeof instant === 'string' ? new Date(instant) : instant)
}

export function clock(instant: string | Date, zone = viewerZone()): string {
  return parts(instant, zone, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
}

/** «10:00–10:45» en la zona de quien mira. */
export function timeRange(startsAt: string, minutes: number, zone = viewerZone()): string {
  const end = new Date(new Date(startsAt).getTime() + minutes * 60_000)
  return `${clock(startsAt, zone)}–${clock(end, zone)}`
}

/** Número de día y nombre del día, para el encabezado de cada grupo de la agenda. */
export function dayHeading(instant: string, zone = viewerZone()): { number: string; weekday: string } {
  return { number: parts(instant, zone, { day: 'numeric' }), weekday: parts(instant, zone, { weekday: 'long' }) }
}

/** «jueves 26 de noviembre, 10:00 a 10:45». */
export function longWhen(startsAt: string, minutes: number, zone = viewerZone()): string {
  const end = new Date(new Date(startsAt).getTime() + minutes * 60_000)
  const day = parts(startsAt, zone, { weekday: 'long', day: 'numeric', month: 'long' })
  return `${day.charAt(0).toUpperCase()}${day.slice(1)}, ${clock(startsAt, zone)} a ${clock(end, zone)}`
}

/** «Hoy, 10:00», «Mañana, 10:00» o «Jueves 26, 10:00» según la cercanía, en la zona de quien mira. */
export function relativeWhen(startsAt: string, now = new Date(), zone = viewerZone()): string {
  const day = dayKey(startsAt, zone)
  const today = dayKey(now, zone)
  const tomorrow = dayKey(new Date(now.getTime() + 86_400_000), zone)
  const prefix = day === today ? 'Hoy' : day === tomorrow ? 'Mañana'
    : (() => { const d = parts(startsAt, zone, { weekday: 'long', day: 'numeric' }); return d.charAt(0).toUpperCase() + d.slice(1) })()
  return `${prefix}, ${clock(startsAt, zone)}`
}

/** Si la cita está en otra zona, la hora en esa zona para quien la agendó (p. ej., «10:00 en Lima»). */
export function otherZoneNote(startsAt: string, zoneId: string, zone = viewerZone()): string | null {
  if (zoneId === zone || clock(startsAt, zoneId) === clock(startsAt, zone)) return null
  return `${clock(startsAt, zoneId)} en ${zoneLabel(zoneId).replace('Hora de ', '')}`
}
