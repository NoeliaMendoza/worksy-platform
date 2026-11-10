import { ShieldAlert, ShieldCheck, ShieldQuestion } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { confidenceLabel } from '@/features/ranking/labels'
import type { ConfidenceLevel } from '@/features/ranking/types'

const config = {
  alta: { tone: 'high', Icon: ShieldCheck },
  media: { tone: 'mid', Icon: ShieldQuestion },
  baja: { tone: 'low', Icon: ShieldAlert },
} as const

/** Confianza del modelo: palabra + icono + color (nunca solo color). */
export function ConfidenceBadge({ level, className }: { level: ConfidenceLevel; className?: string }) {
  const { tone, Icon } = config[level]
  return (
    <Badge tone={tone} className={className}>
      <Icon aria-hidden="true" />
      {confidenceLabel[level]}
    </Badge>
  )
}
