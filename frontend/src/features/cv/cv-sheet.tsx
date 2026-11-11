import { cn } from '@/lib/cn'

type CvSheetProps = {
  /** Mientras sube, una línea recorre la hoja de arriba abajo. */
  scanning?: boolean
  /** Con un archivo elegido o ya subido, la hoja muestra líneas de texto. */
  filled?: boolean
  className?: string
}

/**
 * Hoja A4 con la esquina doblada: la forma reconocible de un CV.
 * Es decorativa; el estado se comunica siempre con texto al lado.
 */
export function CvSheet({ scanning = false, filled = false, className }: CvSheetProps) {
  return (
    <div aria-hidden="true" className={cn('relative w-[84px] shrink-0', className)}>
      <svg viewBox="0 0 84 119" className="block w-full drop-shadow-[0_10px_18px_rgb(11_31_75/0.14)]">
        <path d="M1 1h62l20 20v97H1z" className="fill-white stroke-azure-200" strokeWidth="1.5" strokeLinejoin="round" />
        <path d="M63 1v20h20" className="fill-azure-100 stroke-azure-200" strokeWidth="1.5" strokeLinejoin="round" />
        {filled ? (
          <g className="fill-azure-200">
            <rect x="12" y="16" width="28" height="5" rx="2.5" className="fill-navy-700" />
            <rect x="12" y="26" width="40" height="3" rx="1.5" />
            <rect x="12" y="40" width="60" height="3" rx="1.5" />
            <rect x="12" y="47" width="54" height="3" rx="1.5" />
            <rect x="12" y="54" width="58" height="3" rx="1.5" />
            <rect x="12" y="68" width="60" height="3" rx="1.5" />
            <rect x="12" y="75" width="46" height="3" rx="1.5" />
            <rect x="12" y="89" width="56" height="3" rx="1.5" />
            <rect x="12" y="96" width="38" height="3" rx="1.5" />
          </g>
        ) : (
          <path d="M42 46v28M30 58l12-12 12 12" className="stroke-azure-500" strokeWidth="3" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        )}
      </svg>
      {scanning && (
        <span
          className="absolute inset-x-1 top-1 h-0.5 animate-scan rounded-full bg-azure-500 shadow-[0_0_12px_2px_rgb(61_106_224/0.45)] [--scan-distance:112px]"
        />
      )}
    </div>
  )
}
