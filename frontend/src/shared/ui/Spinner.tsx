interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg'
}

function Spinner({ size = 'md' }: SpinnerProps) {
  const sizeClass = size === 'sm' ? 'spinner-sm' : size === 'lg' ? 'spinner-lg' : ''
  return <span className={['spinner', sizeClass].filter(Boolean).join(' ')} role="status" aria-label="Ładowanie" />
}

export default Spinner
