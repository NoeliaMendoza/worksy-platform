import { ArrowDown, ArrowUp, Plus, Trash2, X } from 'lucide-react'
import { useId } from 'react'
import { Button } from '@/components/ui/button'
import { ChoicePills } from '@/components/ui/choice-pills'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { MAX_CRITERIA, MAX_OPTIONS, newCriterion, type CriterionDraft } from './criterion-draft'
import { kindLabel } from './labels'

type Props = {
  value: CriterionDraft[]
  onChange: (next: CriterionDraft[]) => void
  disabled?: boolean
}

/** Lista editable de criterios: Sí/No o varias opciones (la primera, la más favorable). */
export function CriteriaEditor({ value, onChange, disabled }: Props) {
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
        <h2 id="criterios-titulo" className="text-lg font-semibold text-ink-900">
          Qué se evaluará en cada CV
        </h2>
        <p className="mt-1 text-sm text-ink-500">
          El modelo responde cada pregunta con la evidencia del CV para ordenar la revisión. Las personas candidatas
          también las verán.
        </p>
      </div>

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
  onPatch,
  onMove,
  onRemove,
}: {
  criterion: CriterionDraft
  index: number
  total: number
  disabled?: boolean
  onPatch: (patch: Partial<CriterionDraft>) => void
  onMove: (delta: number) => void
  onRemove: () => void
}) {
  const labelId = useId()
  const questionId = useId()
  const n = index + 1
  const iconButton =
    'grid size-9 cursor-pointer place-items-center rounded-full text-ink-500 hover:bg-azure-50 hover:text-navy-900 disabled:cursor-not-allowed disabled:opacity-40'

  return (
    <article aria-label={`Criterio ${n}`} className="rounded-card bg-white p-5 shadow-soft">
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
        <Field id={labelId} label="Nombre corto">
          <Input
            id={labelId}
            value={c.label}
            maxLength={60}
            placeholder="Spring Boot"
            onChange={(e) => onPatch({ label: e.target.value })}
            readOnly={disabled}
            aria-describedby={`${labelId}-error`}
          />
        </Field>
        <Field id={questionId} label="Pregunta">
          <Textarea
            id={questionId}
            rows={2}
            value={c.question}
            maxLength={2000}
            placeholder="¿Tiene experiencia profesional con Spring Boot?"
            onChange={(e) => onPatch({ question: e.target.value })}
            readOnly={disabled}
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
      />

      {c.kind === 'CHOICE' && (
        <fieldset className="mt-4 flex flex-col gap-2">
          <legend className="mb-2 text-sm font-semibold text-ink-900">
            Opciones <span className="font-normal text-ink-500">(de la más a la menos favorable)</span>
          </legend>
          {c.options.map((option, oi) => (
            <div key={oi} className="flex items-center gap-2">
              <Input
                value={option}
                maxLength={80}
                aria-label={`Opción ${oi + 1} del criterio ${n}`}
                placeholder={oi === 0 ? 'Más de 3 años' : oi === 1 ? '1 a 3 años' : 'Menos de 1 año'}
                onChange={(e) => onPatch({ options: c.options.map((o, j) => (j === oi ? e.target.value : o)) })}
                readOnly={disabled}
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
          ))}
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
    </article>
  )
}
