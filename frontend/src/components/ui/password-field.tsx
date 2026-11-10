import { Eye, EyeOff } from 'lucide-react'
import { useState, type ComponentProps } from 'react'
import { Field } from './field'
import { Input } from './input'

type PasswordFieldProps = Omit<ComponentProps<'input'>, 'type' | 'id'> & {
  id: string
  label: string
  error?: string
  /** Texto de ayuda bajo la etiqueta (p. ej., la longitud mínima). */
  hint?: string
}

/** Campo de contraseña con botón para mostrarla. */
export function PasswordField({ id, label, error, hint, ...input }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)
  const describedBy = [hint ? `${id}-hint` : null, `${id}-error`].filter(Boolean).join(' ')
  return (
    <Field
      id={id}
      label={label}
      error={error}
      aside={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-controls={id}
          aria-pressed={visible}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full py-1 text-sm font-semibold text-azure-600 hover:text-navy-900"
        >
          {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
          {visible ? 'Ocultar' : 'Mostrar'}
        </button>
      }
    >
      {hint && (
        <p id={`${id}-hint`} className="-mt-1 text-sm text-ink-500">
          {hint}
        </p>
      )}
      <Input
        id={id}
        type={visible ? 'text' : 'password'}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...input}
      />
    </Field>
  )
}
