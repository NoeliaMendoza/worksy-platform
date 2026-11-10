import type { ComponentProps } from 'react'
import { cn } from '@/lib/cn'

/** Bloque de carga. Decorativo: el estado se anuncia aparte con texto. */
export function Skeleton({ className, ...props }: ComponentProps<'div'>) {
  return <div aria-hidden="true" className={cn('animate-pulse rounded-full bg-azure-100', className)} {...props} />
}
