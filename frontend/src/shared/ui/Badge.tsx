import type { ReactNode } from 'react'

type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'error'

interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
}

function Badge({ tone = 'neutral', children }: BadgeProps) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

export default Badge
