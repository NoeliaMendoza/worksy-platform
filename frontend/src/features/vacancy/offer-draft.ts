import type { OfferDetails } from './api'

export const contractLabels = { INDEFINITE: 'Indefinido', FIXED_TERM: 'Plazo fijo', SERVICES: 'Prestación de servicios', INTERNSHIP: 'Prácticas', OTHER: 'Otro' }
export const scheduleLabels = { FULL_TIME: 'Tiempo completo', PART_TIME: 'Tiempo parcial', FLEXIBLE: 'Flexible' }
export const periodLabels = { HOUR: 'Por hora', MONTH: 'Mensual', YEAR: 'Anual' }
export type OfferDraft = { [K in keyof OfferDetails]: string }
export const emptyOffer: OfferDraft = { contractType: '', workSchedule: '', area: '', experienceLevel: '', responsibilities: '', requirements: '', desirable: '', benefits: '', schedule: '', remoteConditions: '', salaryMin: '', salaryMax: '', currency: '', salaryPeriod: '' }
export function offerDraft(offer?: OfferDetails | null): OfferDraft {
  return Object.fromEntries(Object.keys(emptyOffer).map((key) => [key, String(offer?.[key as keyof OfferDetails] ?? '')])) as OfferDraft
}
export function offerValue(d: OfferDraft): OfferDetails {
  return { ...Object.fromEntries(Object.entries(d).map(([k, v]) => [k, v.trim() || null])),
    salaryMin: d.salaryMin.trim() ? Number(d.salaryMin) : null,
    salaryMax: d.salaryMax.trim() ? Number(d.salaryMax) : null,
  } as OfferDetails
}
export function offerErrors(d: OfferDraft, publish: boolean): Partial<Record<keyof OfferDetails, string>> {
  const errors: Partial<Record<keyof OfferDetails, string>> = {}
  if (publish) for (const key of ['contractType', 'workSchedule', 'responsibilities', 'requirements'] as const) {
    if (!d[key].trim()) errors[key] = 'Completa este campo para publicar.'
  }
  if ([d.salaryMin, d.salaryMax, d.currency, d.salaryPeriod].some((v) => v.trim())) {
    if (!/^\d+(\.\d{1,2})?$/.test(d.salaryMin) || !/^\d+(\.\d{1,2})?$/.test(d.salaryMax)
      || Number(d.salaryMax) < Number(d.salaryMin) || Number(d.salaryMax) > 999999999999.99) {
      errors.salaryMin = 'Indica ambos importes, con hasta dos decimales y un máximo mayor o igual al mínimo.'
    }
    try { if (!/^[A-Z]{3}$/.test(d.currency) || !Intl.supportedValuesOf('currency').includes(d.currency)) errors.currency = 'Indica una moneda ISO válida, por ejemplo USD.' }
    catch { if (!/^[A-Z]{3}$/.test(d.currency)) errors.currency = 'Indica una moneda ISO de tres letras.' }
    if (!d.salaryPeriod) errors.salaryPeriod = 'Selecciona el período del salario.'
  }
  return errors
}

