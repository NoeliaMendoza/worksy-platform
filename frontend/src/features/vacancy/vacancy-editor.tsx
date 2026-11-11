import { LoaderCircle, Send } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { ChoicePills } from '@/components/ui/choice-pills'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/http'
import { companyVacancies, type CompanyVacancy, type Modality, type VacancyInput } from './api'
import { CriteriaEditor } from './criteria-editor'
import { newCriterion, type CriterionDraft } from './criterion-draft'
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

  function input(): VacancyInput {
    return {
      title: title.trim(),
      location: location.trim(),
      modality,
      description: description.trim(),
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
    setFormError('')
    const found: Errors = {}
    if (title.trim().length < 3) found.title = 'Escribe el título del puesto (al menos 3 caracteres).'
    if (location.trim().length < 2) found.location = 'Escribe la ciudad o "Remoto".'
    if (description.trim().length < 20) found.description = 'Describe el puesto en al menos 20 caracteres.'
    setErrors(found)
    const first = found.title ? titleId : found.location ? locationId : found.description ? descriptionId : null
    if (first) return document.getElementById(first)?.focus()

    setBusy(publish ? 'publish' : 'save')
    try {
      let saved = initial
        ? await companyVacancies.update(token, initial.id, input())
        : await companyVacancies.create(token, input())
      if (publish) saved = await companyVacancies.publish(token, saved.id)
      onSaved(saved)
    } catch (err) {
      setFormError(errorMessage(err, 'No se pudo guardar la vacante. Vuelve a intentarlo.'))
    } finally {
      setBusy(null)
    }
  }

  return (
    <form noValidate onSubmit={(e) => submit(e, false)} className="flex flex-col gap-8">
      {formError && (
        <Notice tone="error" autoFocus>
          {formError}
        </Notice>
      )}

      <section aria-labelledby="puesto-titulo" className="flex flex-col gap-5 rounded-card bg-white p-5 shadow-soft sm:p-6">
        <h2 id="puesto-titulo" className="text-lg font-semibold text-ink-900">
          El puesto
        </h2>
        <Field id={titleId} label="Título" error={errors.title}>
          <Input
            id={titleId}
            value={title}
            maxLength={160}
            placeholder="Desarrollador/a backend Java"
            onChange={(e) => setTitle(e.target.value)}
            aria-invalid={errors.title ? true : undefined}
            aria-describedby={`${titleId}-error`}
            readOnly={busy !== null}
          />
        </Field>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field id={locationId} label="Ubicación" error={errors.location}>
            <Input
              id={locationId}
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
            legend="Modalidad"
            value={modality}
            onChange={setModality}
            options={(['PRESENCIAL', 'HIBRIDO', 'REMOTO'] as const).map((m) => ({ value: m, label: modalityLabel[m] }))}
          />
        </div>
        <Field id={descriptionId} label="Descripción" error={errors.description}>
          <Textarea
            id={descriptionId}
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

      <CriteriaEditor value={criteria} onChange={setCriteria} disabled={busy !== null} />

      <div className="flex flex-col-reverse gap-3 border-t border-ink-100 pt-6 sm:flex-row sm:justify-end">
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
