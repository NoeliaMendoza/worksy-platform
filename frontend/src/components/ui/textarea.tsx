import type { ComponentProps } from 'react'
import { cn } from '@/lib/cn'

/** Área de texto con el mismo estilo que Input. */
export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return (
    <textarea
      className={cn(
        'w-full min-w-0 rounded-md bg-white px-3 py-2 text-base leading-relaxed text-ink-900 ring-1 ring-ink-300 transition-shadow ring-inset placeholder:text-ink-500 sm:text-sm',
        'hover:ring-azure-400 focus-visible:ring-2 focus-visible:ring-azure-500 focus-visible:outline-none',
        'aria-invalid:ring-2 aria-invalid:ring-low-700',
        className,
      )}
      {...props}
    />
  )
}
