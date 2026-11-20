import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import type { OfferDetails } from './api'

import { contractLabels, scheduleLabels, periodLabels, type OfferDraft } from './offer-draft'

const selectClass = 'h-10 min-w-0 w-full rounded-md border border-ink-100 bg-white px-3 text-base sm:text-sm'
export function OfferEditor({ value, onChange, disabled, errors }: { value: OfferDraft; onChange: (next: OfferDraft) => void; disabled: boolean; errors: Partial<Record<keyof OfferDetails, string>> }) {
  const change = (key: keyof OfferDraft, text: string) => onChange({ ...value, [key]: text })
  const select = (key: 'contractType' | 'workSchedule' | 'salaryPeriod', label: string, options: Record<string, string>) => <Field id={`offer-${key}`} label={label} error={errors[key]}>
    <select id={`offer-${key}`} value={value[key]} disabled={disabled} onChange={(e) => change(key, e.target.value)} className={selectClass} aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `offer-${key}-error` : undefined}>
      <option value="">Seleccionar</option>{Object.entries(options).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
    </select></Field>
  const text = (key: keyof OfferDraft, label: string, max: number, multiline = false) => <Field key={key} id={`offer-${key}`} label={label} error={errors[key]}>
    {multiline ? <Textarea id={`offer-${key}`} rows={3} maxLength={max} value={value[key]} readOnly={disabled} onChange={(e) => change(key, e.target.value)} aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `offer-${key}-error` : undefined} />
      : <Input id={`offer-${key}`} maxLength={max} value={value[key]} readOnly={disabled} onChange={(e) => change(key, key === 'currency' ? e.target.value.toUpperCase() : e.target.value)} aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `offer-${key}-error` : undefined} />}
  </Field>
  return <section aria-labelledby="condiciones-oferta" className="space-y-4 rounded-card border border-ink-100 bg-white p-4 shadow-soft">
    <h2 id="condiciones-oferta" className="text-base font-semibold">Condiciones y contenido de la oferta</h2>
    <p className="text-sm text-ink-700">Contrato, jornada, responsabilidades y requisitos son obligatorios al publicar. Puedes completarlos después de guardar un borrador.</p>
    <div className="grid gap-4 sm:grid-cols-2">
      {select('contractType', 'Tipo de contrato *', contractLabels)}{select('workSchedule', 'Jornada *', scheduleLabels)}
      {text('area', 'Área (opcional)', 120)}{text('experienceLevel', 'Nivel de experiencia (opcional)', 120)}
      {text('responsibilities', 'Responsabilidades *', 10000, true)}{text('requirements', 'Requisitos indispensables *', 10000, true)}
      {text('desirable', 'Conocimientos deseables (opcional)', 10000, true)}{text('benefits', 'Beneficios (opcional)', 10000, true)}
      {text('schedule', 'Horario (opcional)', 1000, true)}{text('remoteConditions', 'Condiciones del trabajo remoto (opcional)', 2000, true)}
    </div>
    <fieldset className="space-y-3 border-t border-ink-100 pt-4"><legend className="text-sm font-semibold">Rango salarial (opcional)</legend>
      <p className="text-xs text-ink-700">Si lo informas, completa ambos importes, moneda y período. Se mostrará en la oferta pública.</p>
      <div className="grid gap-4 sm:grid-cols-2">
        {(['salaryMin', 'salaryMax'] as const).map((key) => <Field key={key} id={`offer-${key}`} label={key === 'salaryMin' ? 'Salario mínimo' : 'Salario máximo'} error={errors[key]}><Input id={`offer-${key}`} type="number" min="0" max="999999999999.99" step="0.01" value={value[key]} readOnly={disabled} onChange={(e) => change(key, e.target.value)} aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `offer-${key}-error` : undefined} /></Field>)}
        {text('currency', 'Moneda (código ISO, por ejemplo USD)', 3)}{select('salaryPeriod', 'Período del salario', periodLabels)}
      </div>
    </fieldset>
  </section>
}

export function OfferContent({ offer }: { offer?: OfferDetails | null }) {
  if (!offer) return null
  const metadata = [['Contrato', offer.contractType && contractLabels[offer.contractType]], ['Jornada', offer.workSchedule && scheduleLabels[offer.workSchedule]], ['Área', offer.area], ['Experiencia', offer.experienceLevel]]
  const sections = [['Responsabilidades', offer.responsibilities], ['Requisitos indispensables', offer.requirements], ['Conocimientos deseables', offer.desirable], ['Beneficios', offer.benefits], ['Horario', offer.schedule], ['Condiciones del trabajo remoto', offer.remoteConditions]]
  return <section aria-label="Condiciones de la oferta" className="min-w-0 space-y-4 break-words rounded-card border border-ink-100 bg-white p-4 text-sm shadow-soft">
    <dl className="grid gap-3 sm:grid-cols-2">{metadata.filter(([, value]) => value).map(([label, value]) => <div key={label}><dt className="text-ink-500">{label}</dt><dd className="font-medium">{value}</dd></div>)}</dl>
    {offer.salaryMin != null && offer.salaryMax != null && offer.currency && offer.salaryPeriod && <div><h2 className="font-semibold">Rango salarial</h2><p>{offer.salaryMin.toLocaleString('es')}–{offer.salaryMax.toLocaleString('es')} {offer.currency} · {periodLabels[offer.salaryPeriod]}</p></div>}
    {sections.filter(([, value]) => value).map(([label, value]) => <div key={label}><h2 className="font-semibold">{label}</h2><p className="mt-1 whitespace-pre-line leading-relaxed text-ink-700">{value}</p></div>)}
  </section>
}
