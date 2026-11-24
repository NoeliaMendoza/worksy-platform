import { ArrowDown, ArrowUp, Plus, Trash2, X } from 'lucide-react'
import { useId, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ChoicePills } from '@/components/ui/choice-pills'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { MAX_CRITERIA, MAX_OPTIONS, newCriterion, criterionErrors, type CriterionDraft, type CriterionErrors } from './criterion-draft'
import { kindLabel } from './labels'
import { criterionTemplates, optionScore } from './criterion-templates'

type Props = {
  value: CriterionDraft[]
  onChange: (next: CriterionDraft[]) => void
  disabled?: boolean
  showErrors?: boolean
}

/** Lista editable de criterios: Sí/No o varias opciones (la primera, la más favorable). */
export function CriteriaEditor({ value, onChange, disabled, showErrors }: Props) {
  const [templateId, setTemplateId] = useState(criterionTemplates[0]!.id)
  const templateInputId = useId()
  const selectedTemplate = criterionTemplates.find((t) => t.id === templateId)!
  const singleEmpty = value.length === 1 && !value[0]!.label.trim() && !value[0]!.question.trim() && value[0]!.options.every((o) => !o.trim())
  function addTemplate() {
    const next = newCriterion({ ...selectedTemplate.value, options: [...selectedTemplate.value.options] })
    onChange(singleEmpty ? [next] : [...value, next])
  }
  function update(uid: string, patch: Partial<CriterionDraft>) {
    onChange(value.map((c) => (c.uid === uid ? { ...c, ...patch } : c)))
  }
  function move(index: number, delta: number) {
    const next = [...value]
    const [item] = next.splice(index, 1)
    next.splice(index + delta, 0, item!)
    onChange(next)
  }

  return (
    <section aria-labelledby="criterios-titulo" className="flex flex-col gap-4">
      <div>
        <h2 id="criterios-titulo" className="text-base font-semibold text-ink-900">
          Qué se evaluará en cada CV
        </h2>
        <p className="mt-1 text-sm text-ink-500">
          Define preguntas que puedan responderse con información del CV. El análisis orienta la revisión; no confirma por sí solo las habilidades de una persona.
        </p>
        <p className="mt-2 text-xs leading-5 text-ink-700">Todos los criterios tienen el mismo peso. Su orden organiza la lectura y no cambia su importancia. La decisión de avanzar o descartar siempre corresponde a tu equipo.</p>
      </div>

      <div className="space-y-3 rounded-card border border-ink-100 bg-white p-4">
        <div className="flex flex-wrap items-start justify-between gap-2"><h3 className="text-sm font-semibold">Comenzar con una plantilla</h3><p className="text-xs text-ink-500">{value.length} de {MAX_CRITERIA} criterios</p></div>
        <p className="text-sm text-ink-700">Son ejemplos editables. Revisa la tecnología, los rangos y las respuestas antes de publicar.</p>
        <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-end">
          <Field id={templateInputId} label="Plantilla de criterio" className="w-full min-w-0 sm:flex-1"><select id={templateInputId} disabled={disabled} value={templateId} onChange={(e) => setTemplateId(e.target.value)} className="h-10 w-full min-w-0 rounded-md border border-ink-100 bg-white px-3 text-base sm:text-sm">
            {criterionTemplates.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select></Field>
          <Button variant="secondary" disabled={disabled || (!singleEmpty && value.length >= MAX_CRITERIA)} onClick={addTemplate}><Plus aria-hidden="true" />Añadir plantilla</Button>
        </div>
        <p className="text-xs leading-5 text-ink-700">{selectedTemplate.description}</p>
      </div>

      <details className="rounded-card border border-ink-100 bg-white px-4 py-3 text-sm text-ink-700">
        <summary className="min-h-6 cursor-pointer font-semibold">Cómo escribir un buen criterio</summary>
        <ul className="mt-3 list-disc space-y-2 pl-5 leading-relaxed">
          <li>Pregunta por una sola condición verificable en el CV y redacta en positivo: «¿El CV declara experiencia con Java?».</li>
          <li>En «Sí / No», «Sí» es la respuesta favorable. Evita preguntas negativas, porque invertirían el significado de la puntuación.</li>
          <li>En «Varias opciones», ordena las respuestas de más a menos favorables. Usa rangos sin solapamiento y considera una respuesta como «No se indica en el CV».</li>
          <li>Evalúa conocimientos y experiencia relacionados con el puesto. Evita datos como edad, género o estado civil.</li>
          <li>La ausencia de información no demuestra falta de experiencia. Verifica el resultado en el CV o en una entrevista.</li>
        </ul>
      </details>

      {value.length === 0 && (
        <p className="rounded-card bg-azure-100 p-4 text-sm text-ink-700">
          Agrega al menos un criterio para poder publicar la vacante.
        </p>
      )}

      <ol className="flex flex-col gap-3">
        {value.map((c, i) => (
          <li key={c.uid}>
            <CriterionCard
              criterion={c}
              index={i}
              total={value.length}
              disabled={disabled}
              errors={showErrors ? criterionErrors(c) : {}}
              onPatch={(patch) => update(c.uid, patch)}
              onMove={(delta) => move(i, delta)}
              onRemove={() => onChange(value.filter((x) => x.uid !== c.uid))}
            />
          </li>
        ))}
      </ol>

      <Button
        variant="secondary"
        className="self-start"
        disabled={disabled || value.length >= MAX_CRITERIA}
        onClick={() => onChange([...value, newCriterion()])}
      >
        <Plus aria-hidden="true" />
        Agregar criterio
      </Button>
    </section>
  )
}

function CriterionCard({
  criterion: c,
  index,
  total,
  disabled,
  errors,
  onPatch,
  onMove,
  onRemove,
}: {
  criterion: CriterionDraft
  index: number
  total: number
  disabled?: boolean
  errors: CriterionErrors
  onPatch: (patch: Partial<CriterionDraft>) => void
  onMove: (delta: number) => void
  onRemove: () => void
}) {
  const labelId = useId()
  const questionId = useId()
  const optionsErrorId = useId()
  const n = index + 1
  const iconButton =
    'grid size-9 cursor-pointer place-items-center rounded-full text-ink-500 hover:bg-azure-50 hover:text-navy-900 disabled:cursor-not-allowed disabled:opacity-40'

  return (
    <article data-criterion={c.uid} aria-label={`Criterio ${n}`} className="rounded-card border border-ink-100 bg-white p-4 shadow-soft">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold text-navy-700">Criterio {n}</p>
        <div className="flex gap-1">
          <button type="button" className={iconButton} disabled={disabled || index === 0} onClick={() => onMove(-1)} aria-label={`Subir criterio ${n}`}>
            <ArrowUp className="size-4" aria-hidden="true" />
          </button>
          <button type="button" className={iconButton} disabled={disabled || index === total - 1} onClick={() => onMove(1)} aria-label={`Bajar criterio ${n}`}>
            <ArrowDown className="size-4" aria-hidden="true" />
          </button>
          <button type="button" className={iconButton} disabled={disabled} onClick={onRemove} aria-label={`Quitar criterio ${n}`}>
            <Trash2 className="size-4" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className="mt-3 grid gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
        <Field id={labelId} label="Nombre corto" error={errors.label}>
          <Input
            id={labelId}
            value={c.label}
            maxLength={60}
            placeholder="Spring Boot"
            onChange={(e) => onPatch({ label: e.target.value })}
            readOnly={disabled}
            aria-invalid={!!errors.label}
            aria-describedby={`${labelId}-error`}
          />
        </Field>
        <Field id={questionId} label="Pregunta" error={errors.question}>
          <Textarea
            id={questionId}
            rows={2}
            value={c.question}
            maxLength={2000}
            placeholder="¿Tiene experiencia profesional con Spring Boot?"
            onChange={(e) => onPatch({ question: e.target.value })}
            readOnly={disabled}
            aria-invalid={!!errors.question}
            aria-describedby={`${questionId}-error`}
          />
        </Field>
      </div>

      <ChoicePills
        className="mt-4"
        legend="Tipo de respuesta"
        value={c.kind}
        options={(['YES_NO', 'CHOICE'] as const).map((k) => ({ value: k, label: kindLabel[k] }))}
        onChange={(kind) => onPatch({ kind })}
        disabled={disabled}
      />

      {c.kind === 'YES_NO' && <p className="mt-3 rounded-lg bg-azure-50 p-3 text-xs leading-5 text-ink-700">Respuestas: «Sí» aporta 100 puntos y «No» aporta 0 a este criterio. Redacta la pregunta en positivo. Un «No» del análisis requiere revisión; no descarta a la persona automáticamente.</p>}

      {c.kind === 'CHOICE' && (
        <fieldset className="mt-4 flex flex-col gap-2">
          <legend className="mb-2 text-sm font-semibold text-ink-900">
            Opciones <span className="font-normal text-ink-500">(de la más a la menos favorable)</span>
          </legend>
          <p className="text-xs leading-5 text-ink-700">La primera opción recibe 100 puntos y la última 0. Las intermedias se distribuyen de forma uniforme. Mover una respuesta cambia su puntuación.</p>
          {c.options.map((option, oi) => (
            <div key={oi} className="rounded-lg border border-ink-100 p-3">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2"><span className="text-xs font-semibold text-ink-700">Opción {oi + 1} · {optionScore(oi, c.options.length)} puntos{oi === 0 ? ' · Más favorable' : ''}</span>
                <div className="flex gap-1">
                  {([-1, 1] as const).map((delta) => <button key={delta} type="button" className={iconButton} disabled={disabled || (delta === -1 ? oi === 0 : oi === c.options.length - 1)} aria-label={`${delta === -1 ? 'Subir' : 'Bajar'} opción ${oi + 1} del criterio ${n}`} onClick={() => {
                    const next = [...c.options]; const [item] = next.splice(oi, 1); next.splice(oi + delta, 0, item!); onPatch({ options: next })
                  }}>{delta === -1 ? <ArrowUp className="size-4" aria-hidden="true" /> : <ArrowDown className="size-4" aria-hidden="true" />}</button>)}
                </div>
              </div>
              <div className="flex items-center gap-2">
              <Input
                value={option}
                maxLength={80}
                aria-label={`Opción ${oi + 1} del criterio ${n}`}
                placeholder={oi === 0 ? 'Más de 3 años' : oi === 1 ? '1 a 3 años' : 'Menos de 1 año'}
                onChange={(e) => onPatch({ options: c.options.map((o, j) => (j === oi ? e.target.value : o)) })}
                readOnly={disabled}
                aria-invalid={!!errors.options}
                aria-describedby={errors.options ? optionsErrorId : undefined}
                className="h-11"
              />
              <button
                type="button"
                className={iconButton}
                disabled={disabled || c.options.length <= 2}
                onClick={() => onPatch({ options: c.options.filter((_, j) => j !== oi) })}
                aria-label={`Quitar opción ${oi + 1} del criterio ${n}`}
              >
                <X className="size-4" aria-hidden="true" />
              </button>
              </div>
            </div>
          ))}
          {errors.options && <p id={optionsErrorId} className="text-sm text-low-700">{errors.options}</p>}
          <Button
            variant="ghost"
            size="sm"
            className="self-start"
            disabled={disabled || c.options.length >= MAX_OPTIONS}
            onClick={() => onPatch({ options: [...c.options, ''] })}
          >
            <Plus aria-hidden="true" />
            Agregar opción
          </Button>
        </fieldset>
      )}

      <details className="mt-4 border-t border-ink-100 pt-3 text-sm">
        <summary className="min-h-6 cursor-pointer font-semibold text-ink-700">Ver configuración de evaluación</summary>
        <div className="mt-3 space-y-2 text-ink-700">
          <p className="break-words"><strong>Pregunta:</strong> {c.question.trim() || 'Pendiente de completar'}</p>
          <ul className="space-y-1 text-xs">{(c.kind === 'YES_NO' ? ['Sí', 'No'] : c.options).map((option, i, all) => <li key={i} className="break-words">{option.trim() || `Opción ${i + 1} pendiente`}: {optionScore(i, all.length)} puntos</li>)}</ul>
          <p className="text-xs leading-5">Es la configuración que se enviará al análisis, no una respuesta simulada. La compatibilidad global promedia las puntuaciones de los criterios; no representa una probabilidad de contratación.</p>
        </div>
      </details>
    </article>
  )
}
