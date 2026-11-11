import { LinkIcon, LoaderCircle } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { AppLink } from '@/components/app-link'
import { StateMessage } from '@/components/state-message'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { PasswordField } from '@/components/ui/password-field'
import { navigate, useLocation } from '@/lib/router'
import { ApiError, resetErrorMessage, resetPassword } from './api'
import { AuthLayout } from './auth-layout'
import { PASSWORD_MAX, PASSWORD_MIN } from './validation'

type Errors = { password?: string; confirm?: string }

/** Elegir una contraseña nueva con el enlace del correo (?token=...). */
export function ResetPasswordPage() {
  const { params } = useLocation()
  const [token] = useState(() => params.get('token') ?? '')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState('')
  const [linkInvalid, setLinkInvalid] = useState(false)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const passwordId = useId()
  const confirmId = useId()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const found: Errors = {}
    const length = [...password].length
    if (!password) found.password = 'Escribe una contraseña nueva.'
    else if (length < PASSWORD_MIN || length > PASSWORD_MAX)
      found.password = `Debe tener entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres.`
    if (!found.password && confirm !== password) found.confirm = 'Las contraseñas no coinciden.'
    setErrors(found)
    if (found.password) return document.getElementById(passwordId)?.focus()
    if (found.confirm) return document.getElementById(confirmId)?.focus()

    setSubmitting(true)
    try {
      await resetPassword(token, password)
      // El token ya no sirve: se quita de la URL y del historial.
      navigate('/restablecer', { replace: true })
      setDone(true)
    } catch (err) {
      if (err instanceof ApiError && err.code === 'password.reset.invalid') setLinkInvalid(true)
      else setFormError(resetErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (!token && !done) {
    return (
      <AuthLayout>
        <StateMessage
          icon={LinkIcon}
          title="Falta el enlace"
          description="Abre esta página desde el enlace del correo de recuperación, o pide uno nuevo."
          action={<AppLink href="/recuperar">Pedir un enlace nuevo</AppLink>}
          className="bg-transparent px-0 py-4 shadow-none"
        />
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h1 className="text-3xl leading-tight font-light tracking-tight text-navy-900">
        Elige una contraseña <span className="font-semibold">nueva</span>
      </h1>

      {done ? (
        <div className="mt-8 flex flex-col gap-6">
          <Notice tone="success" autoFocus>
            <p className="font-semibold">Tu contraseña se cambió.</p>
            <p className="mt-1">Por seguridad cerramos las sesiones abiertas en otros dispositivos.</p>
          </Notice>
          <Button className="h-12 w-full text-base" onClick={() => navigate('/')}>
            Iniciar sesión
          </Button>
        </div>
      ) : linkInvalid ? (
        <div className="mt-8 flex flex-col gap-6">
          <Notice tone="error" autoFocus>
            El enlace no es válido o ya caducó. Los enlaces valen 30 minutos y funcionan una sola vez.
          </Notice>
          <AppLink href="/recuperar">Pedir un enlace nuevo</AppLink>
        </div>
      ) : (
        <>
          <p className="mt-2 text-base text-ink-700">Al guardarla, se cerrarán tus sesiones abiertas.</p>
          {formError && (
            <Notice tone="error" autoFocus className="mt-8">
              {formError}
            </Notice>
          )}
          <form noValidate onSubmit={onSubmit} className="mt-8 flex flex-col gap-5">
            <PasswordField
              id={passwordId}
              label="Contraseña nueva"
              hint={`Entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres. Una frase larga es más segura y fácil de recordar.`}
              error={errors.password}
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              readOnly={submitting}
            />
            <PasswordField
              id={confirmId}
              label="Repite la contraseña"
              error={errors.confirm}
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              readOnly={submitting}
            />
            <Button type="submit" className="h-12 w-full text-base" disabled={submitting}>
              {submitting && <LoaderCircle className="animate-spin" aria-hidden="true" />}
              {submitting ? 'Guardando…' : 'Guardar contraseña'}
            </Button>
          </form>
        </>
      )}
    </AuthLayout>
  )
}
