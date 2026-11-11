import { ArrowLeft, LoaderCircle } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { AppLink } from '@/components/app-link'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { PasswordField } from '@/components/ui/password-field'
import { useAuth } from '@/features/auth/auth-context'
import { AuthLayout } from '@/features/auth/auth-layout'
import { EMAIL_PATTERN, PASSWORD_MAX, PASSWORD_MIN } from '@/features/auth/validation'
import { errorMessage } from '@/lib/http'
import { navigate } from '@/lib/router'
import { registerCompany } from './api'

type Errors = { company?: string; email?: string; password?: string }

/** Autoregistro: crea la empresa y la cuenta de su administración, y entra con ella. */
export function RegisterCompanyPage() {
  const { login } = useAuth()
  const [company, setCompany] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const companyId = useId()
  const emailId = useId()
  const passwordId = useId()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const found: Errors = {}
    const name = company.trim()
    if (name.length < 2) found.company = 'Escribe el nombre de la empresa (al menos 2 caracteres).'
    if (!email.trim()) found.email = 'Escribe tu correo de trabajo.'
    else if (!EMAIL_PATTERN.test(email.trim())) found.email = 'Escribe un correo válido, por ejemplo nombre@empresa.com.'
    const length = [...password].length
    if (length < PASSWORD_MIN || length > PASSWORD_MAX)
      found.password = `La contraseña debe tener entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres.`
    setErrors(found)
    const first = found.company ? companyId : found.email ? emailId : found.password ? passwordId : null
    if (first) return document.getElementById(first)?.focus()

    setSubmitting(true)
    try {
      await registerCompany(name, email.trim(), password)
      navigate('/', { replace: true })
      await login(email.trim(), password)
    } catch (err) {
      setFormError(errorMessage(err, 'No se pudo registrar la empresa. Vuelve a intentarlo.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <AppLink href="/" className="inline-flex items-center gap-1.5 text-sm">
        <ArrowLeft className="size-4" aria-hidden="true" />
        Volver a iniciar sesión
      </AppLink>
      <h1 className="mt-6 text-3xl leading-tight font-light tracking-tight text-navy-900">
        Registra tu <span className="font-semibold">empresa</span>
      </h1>
      <p className="mt-2 text-base text-ink-700">Crea la cuenta de tu empresa. Después podrás invitar a tu equipo.</p>

      {formError && (
        <Notice tone="error" autoFocus className="mt-6">
          {formError}
        </Notice>
      )}

      <form noValidate onSubmit={onSubmit} className="mt-7 flex flex-col gap-5">
        <Field id={companyId} label="Nombre de la empresa" error={errors.company}>
          <Input
            id={companyId}
            autoComplete="organization"
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            maxLength={160}
            aria-invalid={errors.company ? true : undefined}
            aria-describedby={`${companyId}-error`}
            readOnly={submitting}
          />
        </Field>
        <Field id={emailId} label="Tu correo de trabajo" error={errors.email}>
          <Input
            id={emailId}
            type="email"
            inputMode="email"
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="nombre@empresa.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={`${emailId}-error`}
            readOnly={submitting}
          />
        </Field>
        <PasswordField
          id={passwordId}
          label="Contraseña"
          hint={`Entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres. Una frase larga es más segura y fácil de recordar.`}
          error={errors.password}
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          readOnly={submitting}
        />
        <Button type="submit" className="mt-1 h-12 w-full text-base" disabled={submitting}>
          {submitting && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          {submitting ? 'Registrando…' : 'Registrar empresa'}
        </Button>
      </form>

      <p className="mt-7 border-t border-ink-100 pt-5 text-sm leading-relaxed text-ink-500">
        Tu cuenta será la de administración de la empresa: podrás publicar vacantes e invitar a tu equipo de selección.
      </p>
    </AuthLayout>
  )
}
