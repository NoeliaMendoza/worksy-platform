import { AppShell } from '@/components/app-shell'
import { RankingView } from '@/features/ranking/ranking-view'
import { useVacancyRanking } from '@/features/ranking/use-vacancy-ranking'
import type { RankingPreview } from '@/mocks/vacancy-ranking'

const previews: RankingPreview[] = ['listo', 'cargando', 'vacio', 'error']

/** Lee ?estado= para revisar los estados de la pantalla sin backend. */
function previewFromUrl(): RankingPreview {
  const value = new URLSearchParams(window.location.search).get('estado')
  return previews.find((p) => p === value) ?? 'listo'
}

export function App() {
  const { state, retry, decide } = useVacancyRanking(previewFromUrl())
  return (
    <AppShell current="vacantes">
      <RankingView state={state} onRetry={retry} onDecide={decide} />
    </AppShell>
  )
}
