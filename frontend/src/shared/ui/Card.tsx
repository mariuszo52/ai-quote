import type { HTMLAttributes } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padded?: boolean
}

function Card({ padded = true, className, children, ...rest }: CardProps) {
  return (
    <div className={['card', padded ? 'card-padded' : '', className ?? ''].filter(Boolean).join(' ')} {...rest}>
      {children}
    </div>
  )
}

export default Card
