import { LoaderCircle, MailPlus, ServerCrash, UserRound } from 'lucide-react'
import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { StateMessage } from '@/components/state-message'
import { PageHeader } from '@/components/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Notice } from '@/components/ui/notice'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/auth-context'
import { EMAIL_PATTERN } from '@/features/auth/validation'
import { errorMessage } from '@/lib/http'
import { inviteRecruiter, members as fetchMembers, myCompany, type Company, type Member } from './api'

type Load = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; company: Company; members: Member[] }

const roleLabel: Record<Member['role'], string> = {
  COMPANY_ADMIN: 'Administración',
  RECRUITER: 'Reclutador/a',
}

const dateFormat = new Intl.DateTimeFormat('es', { dateStyle: 'long' })

/** Equipo de selección de la empresa. Solo la administración invita. */
export function TeamPage() {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const isAdmin = auth.status === 'authenticated' && auth.user.role === 'COMPANY_ADMIN'
  const [load, setLoad] = useState<Load>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [query, setQuery] = useState('')
  const visibleMembers = load.status === 'ready' ? load.members.filter((m) => m.email.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())) : []

  useEffect(() => {
    let active = true
    Promise.all([myCompany(token), fetchMembers(token)]).then(
      ([company, list]) => active && setLoad({ status: 'ready', company, members: list }),
      (err: unknown) => active && setLoad({ status: 'error', message: errorMessage(err, 'No se pudo cargar el equipo.') }),
    )
    return () => {
      active = false
    }
  }, [token, attempt])

  const retry = useCallback(() => {
    setLoad({ status: 'loading' })
    setAttempt((n) => n + 1)
  }, [])

  return (
    <div className="app-page">
      <PageHeader title="Tu equipo" description={load.status === 'ready' ? `Personas de ${load.company.name} que usan Worksy.` : 'Personas de tu empresa que usan Worksy.'} />

      <p role="status" aria-live="polite" className="sr-only">
        {load.status === 'loading' ? 'Cargando el equipo.' : ''}
      </p>

      {load.status === 'loading' && (
        <div className="flex flex-col gap-3" aria-busy="true">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-16 rounded-card" />
          ))}
        </div>
      )}

      {load.status === 'error' && (
        <StateMessage
          tone="error"
          icon={ServerCrash}
          title="No se pudo cargar el equipo"
          description={load.message}
          action={<Button onClick={retry}>Reintentar</Button>}
        />
      )}

      {load.status === 'ready' && (
        <>
          {isAdmin ? (
            <InviteForm
              token={token}
              onInvited={(m) => setLoad({ ...load, members: [...load.members, m] })}
            />
          ) : (
            <p className="rounded-card bg-azure-100 p-4 text-sm text-ink-700">
              Solo la administración de la empresa puede invitar a nuevas personas.
            </p>
          )}

          <section aria-labelledby="miembros">
            <h2 id="miembros" className="text-base font-semibold text-ink-900">
              Miembros <span className="font-normal text-ink-500">({load.members.length})</span>
            </h2>
            <div className="mt-3 rounded-card border border-ink-100 bg-white p-4"><Field id="buscar-miembro" label="Buscar por correo"><Input id="buscar-miembro" type="search" maxLength={254} value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Correo de un miembro del equipo" /></Field></div>
            <p role="status" className="mt-3 text-xs text-ink-500">{visibleMembers.length} de {load.members.length} miembros</p>
            {visibleMembers.length === 0 ? <StateMessage icon={UserRound} title={query.trim() ? 'No hay miembros con ese correo' : 'No hay miembros en el equipo'} description={query.trim() ? 'Prueba otra parte del correo o limpia la búsqueda.' : 'Los miembros de la empresa aparecerán aquí.'}
              action={query.trim() ? <Button variant="secondary" size="sm" onClick={() => setQuery('')}>Limpiar búsqueda</Button> : undefined} /> : <ul className="mt-3 flex flex-col divide-y divide-ink-100 rounded-card border border-ink-100 bg-white shadow-soft">
              {visibleMembers.map((m) => (
                <li key={m.id} className="grid grid-cols-[auto_minmax(0,1fr)] items-start gap-x-3 gap-y-2 p-4 sm:grid-cols-[auto_minmax(0,1fr)_auto]">
                  <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-2xl bg-azure-100 text-navy-700">
                    <UserRound className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="break-all text-sm font-semibold text-ink-900">
                      {m.email}
                    </p>
                    <p className="mt-1 text-xs leading-5 text-ink-500">Desde el {dateFormat.format(new Date(m.createdAt))}</p>
                  </div>
                  <Badge className="col-start-2 justify-self-start sm:col-start-auto" tone={m.role === 'COMPANY_ADMIN' ? 'azure' : 'neutral'}>{roleLabel[m.role]}</Badge>
                </li>
              ))}
            </ul>}
          </section>
        </>
      )}
    </div>
  )
}

function InviteForm({ token, onInvited }: { token: string; onInvited: (m: Member) => void }) {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [result, setResult] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [sending, setSending] = useState(false)
  const emailId = useId()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setResult(null)
    const value = email.trim()
    const found = !value ? 'Escribe el correo de la persona.' : !EMAIL_PATTERN.test(value) ? 'Escribe un correo válido.' : ''
    setError(found)
    if (found) return document.getElementById(emailId)?.focus()

    setSending(true)
    try {
      const member = await inviteRecruiter(token, value)
      onInvited(member)
      setEmail('')
      setResult({ tone: 'success', text: `Invitación enviada a ${member.email}. El enlace vale 72 horas.` })
    } catch (err) {
      setResult({ tone: 'error', text: errorMessage(err, 'No se pudo enviar la invitación.') })
    } finally {
      setSending(false)
    }
  }

  return (
    <section aria-labelledby="invitar" className="rounded-card border border-ink-100 bg-white p-4 shadow-soft">
      <h2 id="invitar" className="flex items-center gap-2 text-base font-semibold text-ink-900">
        <MailPlus className="size-5 text-navy-700" aria-hidden="true" />
        Invitar a un reclutador
      </h2>
      <p className="mt-1 text-sm text-ink-500">Le llegará un correo con un enlace para activar su cuenta y elegir su contraseña.</p>
      {result && (
        <Notice tone={result.tone} autoFocus className="mt-4">
          {result.text}
        </Notice>
      )}
      <form noValidate onSubmit={onSubmit} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-start">
        <Field id={emailId} label="Correo" error={error} className="flex-1">
          <Input
            id={emailId}
            type="email"
            inputMode="email"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="nombre@empresa.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={`${emailId}-error`}
            readOnly={sending}
          />
        </Field>
        <Button type="submit" className="sm:mt-7" disabled={sending}>
          {sending && <LoaderCircle className="animate-spin" aria-hidden="true" />}
          {sending ? 'Enviando…' : 'Enviar invitación'}
        </Button>
      </form>
    </section>
  )
}
