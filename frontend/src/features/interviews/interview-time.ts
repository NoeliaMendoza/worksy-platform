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
