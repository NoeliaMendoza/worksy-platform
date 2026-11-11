import type { ComponentProps } from 'react'
import { cn } from '@/lib/cn'

/** Campo de texto. El estado de error se comunica con aria-invalid (y el mensaje, con aria-describedby). */
export function Input({ className, ...props }: ComponentProps<'input'>) {
  return (
    <input
      className={cn(
        'h-12 w-full rounded-2xl bg-white px-4 text-base text-ink-900 ring-1 ring-ink-300 transition-shadow ring-inset placeholder:text-ink-500',
        'hover:ring-azure-400 focus-visible:ring-2 focus-visible:ring-azure-500 focus-visible:outline-none',
        'aria-invalid:ring-2 aria-invalid:ring-low-700',
        'disabled:cursor-not-allowed disabled:bg-azure-50 disabled:text-ink-500',
        className,
      )}
      {...props}
    />
  )
}
