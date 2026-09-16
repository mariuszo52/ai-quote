import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description?: string
  action?: ReactNode
  icon?: ReactNode
}

function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <div className="empty-state">
      {icon}
      <span className="empty-state-title">{title}</span>
      {description && <p className="empty-state-description">{description}</p>}
      {action}
    </div>
  )
}

export default EmptyState
