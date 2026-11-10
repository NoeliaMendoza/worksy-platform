import { ArrowLeft, LoaderCircle, MailCheck } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { AppLink } from '@/components/app-link'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { ApiError, forgotPassword } from './api'
import { AuthLayout } from './auth-layout'
import { EMAIL_PATTERN } from './validation'

/** Pedir el enlace para restablecer la contraseña. La respuesta no revela si la cuenta existe. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const emailId = useId()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const value = email.trim()
    const found = !value ? 'Escribe tu correo.' : !EMAIL_PATTERN.test(value) ? 'Escribe un correo válido, por ejemplo nombre@empresa.com.' : ''
    setError(found)
    if (found) return document.getElementById(emailId)?.focus()

    setSubmitting(true)
    try {
      await forgotPassword(value)
      setSentTo(value)
    } catch (err) {
      setFormError(
        err instanceof ApiError && err.status === 0
          ? 'No se pudo conectar con el servidor. Revisa tu conexión y vuelve a intentarlo.'
          : 'No se pudo enviar la solicitud. Vuelve a intentarlo en unos minutos.',
      )
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
        Recupera tu <span className="font-semibold">contraseña</span>
      </h1>

      {sentTo ? (
        <div className="mt-8 flex flex-col gap-6">
          <Notice tone="success" autoFocus>
            <p className="font-semibold">Revisa tu correo.</p>
            <p className="mt-1">
              Si hay una cuenta con <strong className="font-semibold break-all">{sentTo}</strong>, te enviamos un enlace
              para elegir una contraseña nueva. Vale 30 minutos y funciona una sola vez. Si no lo ves, revisa la carpeta de
              spam.
            </p>
          </Notice>
          <div className="flex items-start gap-3 text-sm text-ink-500">
            <MailCheck className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <p>
              ¿No llegó?{' '}
              <button
                type="button"
                onClick={() => setSentTo(null)}
                className="cursor-pointer font-semibold text-azure-600 underline-offset-4 hover:text-navy-900 hover:underline"
              >
                Pedir otro enlace
              </button>
            </p>
          </div>
        </div>
      ) : (
        <>
          <p className="mt-2 text-base text-ink-700">
            Escribe el correo de tu cuenta y te enviaremos un enlace para elegir una nueva.
          </p>
          {formError && (
            <Notice tone="error" autoFocus className="mt-8">
              {formError}
            </Notice>
          )}
          <form noValidate onSubmit={onSubmit} className="mt-8 flex flex-col gap-5">
            <Field id={emailId} label="Correo" error={error}>
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
                aria-invalid={error ? true : undefined}
                aria-describedby={`${emailId}-error`}
                readOnly={submitting}
              />
            </Field>
            <Button type="submit" className="h-12 w-full text-base" disabled={submitting}>
              {submitting && <LoaderCircle className="animate-spin" aria-hidden="true" />}
              {submitting ? 'Enviando enlace…' : 'Enviar enlace'}
            </Button>
          </form>
        </>
      )}
    </AuthLayout>
  )
}
