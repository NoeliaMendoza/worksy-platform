import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Notice } from '@/components/ui/notice'
import { useAuth } from '@/features/auth/auth-context'
import { myCompany } from '@/features/company/api'
import { companyVacancies, type CompanyVacancy } from '@/features/vacancy/api'
import { formatDate, modalityLabel } from '@/features/vacancy/labels'
import { errorMessage } from '@/lib/http'
import { navigate } from '@/lib/router'
import { useResource } from '@/lib/use-resource'
import { rankingApi, type RankingResponse } from './api'
import { RankingView } from './ranking-view'
import type { Decision, VacancyRanking } from './types'
import type { RankingState } from './use-vacancy-ranking'

function toView(v: CompanyVacancy, company: string, results: RankingResponse): VacancyRanking {
  const words = v.title.split(' ')
  return {
    vacancy: {
      id: v.id,
      title: { lead: words.slice(0, -1).join(' '), emphasis: words.at(-1) ?? v.title },
      company, location: v.location, modality: modalityLabel[v.modality],
      publishedLabel: v.publishedAt ? `Publicada el ${formatDate(v.publishedAt)}` : 'Sin publicar',
      criteria: v.criteria.map((c) => ({ id: c.key, label: c.label, question: c.question })),
      modelVersion: [...new Set(results.candidates.map((c) => c.modelVersion).filter(Boolean))].join(', ') || 'sin asignar',
      analyzedLabel: `${results.candidates.length} CV analizados`,
    },
    candidates: results.candidates.map((c, i) => ({
      id: c.applicationId, rank: i + 1, fullName: c.fullName || c.email,
      headline: c.status === 'NEEDS_REVIEW' ? 'Análisis con dudas: revisa el CV' : c.headline || 'CV analizado',
      location: c.location || '', receivedLabel: `Recibido el ${formatDate(c.appliedAt)}`,
      score: Math.round(c.overallScore * 100),
      confidence: c.confidence >= 0.8 ? 'alta' : c.confidence >= 0.6 ? 'media' : 'baja',
      answers: c.answers.map((a) => ({
        criterionId: a.criterionId, answer: a.answer || 'Sin respuesta',
        outcome: a.score === 1 ? 'cumple' : a.score === 0 ? 'no_cumple' : 'parcial',
        evidence: '', evidenceUnavailable: true,
      })),
      decision: c.decision,
    })),
  }
}

export function RealRankingPage({ id }: { id: string }) {
  const { state: auth } = useAuth()
  const token = auth.status === 'authenticated' ? auth.accessToken : ''
  const { state, retry, refresh, replace } = useResource(
    () => Promise.all([companyVacancies.get(token, id), myCompany(token), rankingApi.get(token, id)]),
    `ranking:${id}:${token}`, 'No se pudo cargar el ranking.',
  )
  const [saving, setSaving] = useState(false)
  const busy = useRef(false)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const results = state.status === 'ready' ? state.data[2] : null

  useEffect(() => {
    if (!results?.pending || saving) return
    const timer = window.setTimeout(refresh, 5000)
    return () => window.clearTimeout(timer)
  }, [results, saving, refresh])

  async function decide(applicationId: string, decision: Decision | null) {
    if (busy.current || state.status !== 'ready') return
    busy.current = true
    setSaving(true)
    setFeedback(null)
    try {
      await rankingApi.decide(token, id, applicationId, decision)
      // Recargar también aplica un posible retiro del consentimiento durante la revisión.
      const latest = await rankingApi.get(token, id)
      replace([state.data[0], state.data[1], latest])
      setFeedback({ tone: 'success', text: 'Decisión guardada.' })
    } catch (err) {
      setFeedback({ tone: 'error', text: errorMessage(err, 'No se pudo confirmar la decisión. Actualiza para comprobar su estado.') })
      throw err
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  const ranking: RankingState = state.status === 'ready'
    ? { status: 'ready', data: toView(state.data[0], state.data[1].name, state.data[2]) }
    : state
  const empty = results && results.received > 0 ? {
    title: results.pending > 0 ? 'Analizando los CV' : results.failed > 0
      ? 'No se pudieron analizar los CV'
      : results.received === 1 ? '1 postulación espera la preselección' : `${results.received} postulaciones esperan la preselección`,
    description: results.pending > 0 ? 'El ranking se actualizará cuando termine el análisis.'
      : results.failed > 0 ? 'Revisa los CV manualmente desde las postulaciones.'
      : 'Solicita el análisis desde las postulaciones de esta vacante.',
    action: <Button onClick={() => navigate(`/vacantes/${id}`)}>Ver postulaciones</Button>,
  } : undefined

  return (
    <>
      {results && (
        <div className="mx-auto flex max-w-[88rem] flex-col gap-3 px-4 pt-6 sm:px-8">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p role="status" className="text-sm text-ink-700">
              {results.candidates.length} analizados · {results.pending} en proceso · {results.failed} fallidos · {results.notRequested} sin solicitar
            </p>
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => navigate(`/vacantes/${id}`)}>Ver postulaciones y CV</Button>
              <Button variant="secondary" onClick={retry} disabled={saving}>Actualizar ranking</Button>
            </div>
          </div>
          {feedback && <Notice tone={feedback.tone}>{feedback.text}</Notice>}
          {saving && <p role="status">Guardando decisión…</p>}
        </div>
      )}
      <RankingView state={ranking} onRetry={retry} onDecide={decide} decisionsDisabled={saving} empty={empty} />
    </>
  )
}
