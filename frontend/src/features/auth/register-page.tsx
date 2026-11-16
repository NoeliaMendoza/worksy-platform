import { LoaderCircle } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { AppLink } from '@/components/app-link'
import { ProviderButton } from '@/components/provider-button'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { PasswordField } from '@/components/ui/password-field'
import { errorMessage } from '@/lib/http'
import { navigate } from '@/lib/router'
import { AccountTypeSwitch } from './account-type-switch'
import { providers as fetchProviders, registerCandidate, type Provider } from './api'
import { useAuth } from './auth-context'
import { AuthLayout } from './auth-layout'
import { EMAIL_PATTERN, PASSWORD_MAX, PASSWORD_MIN } from './validation'

type Errors = { email?: string; password?: string }

/** Registro de candidatos: correo y contraseña, o una cuenta externa (también crea una cuenta de candidato). */
export function RegisterPage() {
  const { login } = useAuth()
  const [providers, setProviders] = useState<Provider[]>([])
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const emailId = useId()
  const passwordId = useId()

  useEffect(() => {
    let active = true
    fetchProviders().then((list) => active && setProviders(list))
    return () => {
      active = false
    }
  }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const found: Errors = {}
    if (!email.trim()) found.email = 'Escribe tu correo.'
    else if (!EMAIL_PATTERN.test(email.trim())) found.email = 'Escribe un correo válido, por ejemplo nombre@correo.com.'
    const length = [...password].length
    if (length < PASSWORD_MIN || length > PASSWORD_MAX)
      found.password = `La contraseña debe tener entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres.`
    setErrors(found)
    if (found.email) return document.getElementById(emailId)?.focus()
    if (found.password) return document.getElementById(passwordId)?.focus()

    setSubmitting(true)
    try {
      await registerCandidate(email.trim(), password)
      navigate('/', { replace: true })
      await login(email.trim(), password)
    } catch (err) {
      setFormError(errorMessage(err, 'No se pudo crear la cuenta. Vuelve a intentarlo.'))
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-4xl leading-tight font-light tracking-tight text-white">
        Crea tu <span className="font-semibold">cuenta</span>
      </h1>
      <p className="mt-2 text-base text-azure-200">Postula a vacantes y sigue cada proceso desde un solo lugar.</p>

      <AccountTypeSwitch current="/registro" />

      {formError && (
        <Notice tone="error" autoFocus className="mt-6">
          {formError}
        </Notice>
      )}

      <form noValidate onSubmit={onSubmit} className="mt-7 flex flex-col gap-5">
        <Field id={emailId} label="Correo" error={errors.email}>
          <Input
            id={emailId}
            type="email"
            inputMode="email"
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="nombre@correo.com"
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
        <Button type="submit" variant="accent" className="mt-1 h-12 w-full text-base" disabled={submitting}>
          {submitting && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </Button>
      </form>

      {providers.length > 0 && (
        <>
          <div className="mt-7 flex items-center gap-3 text-sm text-azure-200" aria-hidden="true">
            <span className="h-px flex-1 bg-white/15" />o regístrate con
            <span className="h-px flex-1 bg-white/15" />
          </div>
          <ul className="mt-4 grid grid-cols-1 gap-2 min-[360px]:grid-cols-3 min-[360px]:gap-1.5 sm:gap-2" aria-label="Registrarse con otra cuenta">
            {providers.map((p) => (
              <li key={p.id} className="min-w-0">
                <ProviderButton provider={p} />
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="mt-8 text-sm text-azure-200">
        ¿Ya tienes cuenta? <AppLink href="/">Inicia sesión</AppLink>
      </p>
    </AuthLayout>
  )
}
