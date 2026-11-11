import { LoaderCircle } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { AppLink } from '@/components/app-link'
import { ProviderButton } from '@/components/provider-button'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { PasswordField } from '@/components/ui/password-field'
import { navigate, useLocation } from '@/lib/router'
import { externalLoginMessage, loginErrorMessage, providers as fetchProviders, type Provider } from './api'
import { AuthLayout } from './auth-layout'
import { useAuth } from './auth-context'
import { EMAIL_PATTERN } from './validation'

type Errors = { email?: string; password?: string }

/** Inicio de sesión: cuentas externas (si están habilitadas) y correo con contraseña. */
export function LoginPage() {
  const { login } = useAuth()
  const { params } = useLocation()
  const [providers, setProviders] = useState<Provider[]>([])
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState(() => externalLoginMessage(params.get('acceso')) ?? '')
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

  // El motivo de un login externo fallido se muestra una vez y se quita de la URL.
  useEffect(() => {
    if (params.has('acceso')) navigate('/', { replace: true })
  }, [params])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const found: Errors = {}
    if (!email.trim()) found.email = 'Escribe tu correo.'
    else if (!EMAIL_PATTERN.test(email.trim())) found.email = 'Escribe un correo válido, por ejemplo nombre@empresa.com.'
    if (!password) found.password = 'Escribe tu contraseña.'
    setErrors(found)
    if (found.email) return document.getElementById(emailId)?.focus()
    if (found.password) return document.getElementById(passwordId)?.focus()

    setSubmitting(true)
    try {
      await login(email.trim(), password)
    } catch (error) {
      setFormError(loginErrorMessage(error))
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-3xl leading-tight font-light tracking-tight text-navy-900">
        Inicia <span className="font-semibold">sesión</span>
      </h1>
      <p className="mt-2 text-base text-ink-700">Entra a tu panel de Worksy.</p>

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
            autoComplete="username"
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

        <div className="flex flex-col gap-2">
          <PasswordField
            id={passwordId}
            label="Contraseña"
            error={errors.password}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            readOnly={submitting}
          />
          <AppLink href="/recuperar" className="self-end text-sm">
            ¿Olvidaste tu contraseña?
          </AppLink>
        </div>

        <Button type="submit" className="h-12 w-full text-base" disabled={submitting}>
          {submitting && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          {submitting ? 'Iniciando sesión…' : 'Iniciar sesión'}
        </Button>
      </form>

      {providers.length > 0 && (
        <>
          <div className="mt-7 flex items-center gap-3 text-sm text-ink-500" aria-hidden="true">
            <span className="h-px flex-1 bg-ink-100" />o continúa con
            <span className="h-px flex-1 bg-ink-100" />
          </div>
          <ul className="mt-4 grid grid-cols-1 gap-2 min-[360px]:grid-cols-3 min-[360px]:gap-1.5 sm:gap-2" aria-label="Iniciar sesión con otra cuenta">
            {providers.map((p) => (
              <li key={p.id} className="min-w-0">
                <ProviderButton provider={p} />
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="mt-7 border-t border-ink-100 pt-5 text-sm leading-relaxed text-ink-500">
        ¿Tu empresa aún no usa Worksy? <AppLink href="/registro-empresa">Regístrala</AppLink>
      </p>
    </AuthLayout>
  )
}
