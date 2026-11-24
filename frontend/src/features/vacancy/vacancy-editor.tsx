import { LoaderCircle, Send } from 'lucide-react'
import { useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { ChoicePills } from '@/components/ui/choice-pills'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/http'
import { companyVacancies, type CompanyVacancy, type Modality, type VacancyInput, type OfferDetails } from './api'
import { OfferEditor, OfferContent } from './offer-details'
import { offerDraft, offerValue, offerErrors } from './offer-draft'
import { CriteriaEditor } from './criteria-editor'
import { newCriterion, criterionErrors, type CriterionDraft } from './criterion-draft'
import { modalityLabel } from './labels'

type Props = {
  token: string
  /** Sin vacante = crear una nueva. */
  initial?: CompanyVacancy
  onSaved: (vacancy: CompanyVacancy) => void
}

type Errors = { title?: string; location?: string; description?: string }

/** Crear o editar una vacante en borrador. Las reglas finas las valida la API y su mensaje se muestra tal cual. */
export function VacancyEditor({ token, initial, onSaved }: Props) {
  const [offer, setOffer] = useState(() => offerDraft(initial?.offer))
  const [offerProblems, setOfferProblems] = useState<Partial<Record<keyof OfferDetails, string>>>({})
  const [preview, setPreview] = useState(false)
  const [showCriteriaErrors, setShowCriteriaErrors] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  const [savedDraft, setSavedDraft] = useState(initial)
  const [title, setTitle] = useState(initial?.title ?? '')
  const [location, setLocation] = useState(initial?.location ?? '')
  const [modality, setModality] = useState<Modality>(initial?.modality ?? 'HIBRIDO')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [criteria, setCriteria] = useState<CriterionDraft[]>(() =>
    initial?.criteria.length
      ? initial.criteria.map((c) =>
          newCriterion({ label: c.label, question: c.question, kind: c.kind, options: c.kind === 'CHOICE' ? c.options : ['', ''] }),
        )
      : [newCriterion()],
  )
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState<null | 'save' | 'publish'>(null)
  const titleId = useId()
  const locationId = useId()
  const descriptionId = useId()
  const publishLabels: Record<string, string> = { contractType: 'contrato', workSchedule: 'jornada', responsibilities: 'responsabilidades', requirements: 'requisitos', salaryMin: 'rango salarial', currency: 'moneda', salaryPeriod: 'período salarial' }
  const missing = [title.trim().length < 3 && 'título', location.trim().length < 2 && 'ubicación', description.trim().length < 20 && 'descripción',
    ...Object.keys(offerErrors(offer, true)).map((key) => publishLabels[key] || key), criteria.length === 0 && 'al menos un criterio',
    ...criteria.flatMap((c, i) => Object.keys(criterionErrors(c)).length ? [`criterio ${i + 1}`] : [])].filter(Boolean)

  function input(): VacancyInput {
    return {
      title: title.trim(),
      location: location.trim(),
      modality,
      description: description.trim(),
      offer: offerValue(offer),
      criteria: criteria.map((c) => ({
        label: c.label.trim(),
        question: c.question.trim(),
        kind: c.kind,
        ...(c.kind === 'CHOICE' ? { options: c.options.map((o) => o.trim()) } : {}),
      })),
    }
  }

  async function submit(e: FormEvent, publish: boolean) {
    e.preventDefault()
    if (busy) return
    setFormError('')
    const found: Errors = {}
    if (title.trim().length < 3) found.title = 'Escribe el título del puesto (al menos 3 caracteres).'
    if (location.trim().length < 2) found.location = 'Escribe la ciudad o "Remoto".'
    if (description.trim().length < 20) found.description = 'Describe el puesto en al menos 20 caracteres.'
    setErrors(found)
    const problems = offerErrors(offer, publish)
    if (publish) setShowCriteriaErrors(true)
    setOfferProblems(problems)
    const first = found.title ? titleId : found.location ? locationId : found.description ? descriptionId : null
    if (first) return document.getElementById(first)?.focus()
    const firstOffer = Object.keys(problems)[0]
    if (firstOffer) return document.getElementById(`offer-${firstOffer}`)?.focus()
    if (publish && criteria.some((c) => Object.keys(criterionErrors(c)).length > 0)) {
      requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[data-criterion] [aria-invalid="true"]')?.focus())
      return
    }

    setBusy(publish ? 'publish' : 'save')
    try {
      let saved = savedDraft
        ? await companyVacancies.update(token, savedDraft.id, input())
        : await companyVacancies.create(token, input())
      setSavedDraft(saved)
      if (publish) saved = await companyVacancies.publish(token, saved.id)
      onSaved(saved)
    } catch (err) {
      setFormError(errorMessage(err, 'No se pudo guardar la vacante. Vuelve a intentarlo.'))
    } finally {
      setBusy(null)
    }
  }

  return (
    <form ref={formRef} noValidate onSubmit={(e) => submit(e, false)} className="flex w-full max-w-5xl flex-col gap-4">
      {formError && (
        <Notice tone="error" autoFocus>
          {formError}
        </Notice>
      )}

      <section aria-labelledby="puesto-titulo" className="flex flex-col gap-4 rounded-card border border-ink-100 bg-white p-4 shadow-soft">
        <h2 id="puesto-titulo" className="text-base font-semibold text-ink-900">
          Oferta pública: el puesto
        </h2>
        <p className="text-sm text-ink-700">Los campos marcados con * son obligatorios. Esta información será visible para quienes consulten la vacante.</p>
        <Field id={titleId} label="Título" error={errors.title} aside={<span aria-hidden="true">*</span>}>
          <Input
            id={titleId}
            aria-required="true"
            value={title}
            maxLength={160}
            placeholder="Desarrollador/a backend Java"
            onChange={(e) => setTitle(e.target.value)}
            aria-invalid={errors.title ? true : undefined}
            aria-describedby={`${titleId}-error`}
            readOnly={busy !== null}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id={locationId} label="Ubicación" error={errors.location} aside={<span aria-hidden="true">*</span>}>
            <Input
              id={locationId}
              aria-required="true"
              value={location}
              maxLength={120}
              placeholder="Lima"
              onChange={(e) => setLocation(e.target.value)}
              aria-invalid={errors.location ? true : undefined}
              aria-describedby={`${locationId}-error`}
              readOnly={busy !== null}
            />
          </Field>
          <ChoicePills
            legend="Modalidad *"
            value={modality}
            onChange={setModality}
            disabled={busy !== null}
            options={(['PRESENCIAL', 'HIBRIDO', 'REMOTO'] as const).map((m) => ({ value: m, label: modalityLabel[m] }))}
          />
        </div>
        <Field id={descriptionId} label="Descripción" error={errors.description} aside={<span aria-hidden="true">*</span>}>
          <Textarea
            id={descriptionId}
            aria-required="true"
            rows={7}
            value={description}
            maxLength={50_000}
            placeholder="Qué hará la persona, con qué equipo y qué ofrece la empresa."
            onChange={(e) => setDescription(e.target.value)}
            aria-invalid={errors.description ? true : undefined}
            aria-describedby={`${descriptionId}-error`}
            readOnly={busy !== null}
          />
        </Field>
      </section>

      <OfferEditor value={offer} onChange={setOffer} disabled={busy !== null} errors={offerProblems} />

      <section aria-label="Evaluación interna" className="space-y-3 border-t border-ink-100 pt-4">
        <h2 className="text-base font-semibold">Evaluación interna</h2>
        <p className="text-sm text-ink-700">Estos criterios organizan la revisión del CV y no se muestran en la oferta pública. La decisión final la toma tu equipo.</p>
        <CriteriaEditor value={criteria} onChange={setCriteria} disabled={busy !== null} showErrors={showCriteriaErrors} />
      </section>

      <Button variant="secondary" className="self-start" aria-expanded={preview} aria-controls="vista-previa-oferta" onClick={() => setPreview(!preview)}>{preview ? 'Ocultar vista previa' : 'Vista previa de la oferta'}</Button>
      {preview && <section id="vista-previa-oferta" aria-label="Vista previa de la oferta" className="space-y-4 rounded-card border border-azure-200 bg-azure-50 p-4">
        <p className="text-xs text-ink-700">Vista previa sin guardar. Solo muestra el contenido público; los campos vacíos se omiten.</p>
        <h2 className="break-words text-xl font-semibold">{title.trim() || 'Título pendiente'}</h2>
        <p className="text-sm">{location.trim() || 'Ubicación pendiente'} · {modalityLabel[modality]}</p>
        <p className="whitespace-pre-line break-words text-sm">{description.trim() || 'Descripción pendiente'}</p>
        <OfferContent offer={offerValue(offer)} />
      </section>}

      <div className="rounded-card border border-ink-100 bg-white p-4 text-sm text-ink-700">
        <p className="font-semibold">Antes de publicar</p>
        <p className="mt-1">{missing.length > 0
          ? `Falta completar o corregir: ${missing.join(', ')}.`
          : 'Los datos principales están completos. Revisa también las preguntas de evaluación antes de publicar.'}</p>
        <p className="mt-1 text-xs">Guardar un borrador no publica la oferta. Al publicar, estará disponible para recibir postulaciones y sus criterios no podrán editarse.</p>
      </div>

      <div className="flex flex-col-reverse gap-3 border-t border-ink-100 pt-4 sm:flex-row sm:justify-end">
        <Button type="submit" variant="secondary" disabled={busy !== null}>
          {busy === 'save' && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          {busy === 'save' ? 'Guardando…' : 'Guardar borrador'}
        </Button>
        <Button disabled={busy !== null || criteria.length === 0} onClick={(e) => submit(e, true)}>
          {busy === 'publish' ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : <Send aria-hidden="true" />}
          {busy === 'publish' ? 'Publicando…' : 'Guardar y publicar'}
        </Button>
      </div>
    </form>
  )
}
