import type { ComponentProps, MouseEvent } from 'react'
import { navigate } from '@/lib/router'
import { cn } from '@/lib/cn'

/** Enlace interno: navega sin recargar, pero sigue siendo un <a> real (abrir en pestaña nueva funciona). */
export function AppLink({ href, className, onClick, ...props }: ComponentProps<'a'> & { href: string }) {
  function handle(e: MouseEvent<HTMLAnchorElement>) {
    onClick?.(e)
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
    e.preventDefault()
    navigate(href)
  }
  return (
    <a
      href={href}
      onClick={handle}
      className={cn('rounded font-semibold text-azure-600 underline-offset-4 hover:text-navy-900 hover:underline', className)}
      {...props}
    />
  )
}
