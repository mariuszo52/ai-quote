import { useId, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react'

interface FieldWrapperProps {
  label?: string
  hint?: string
  error?: string
  htmlFor: string
  children: React.ReactNode
}

function FieldWrapper({ label, hint, error, htmlFor, children }: FieldWrapperProps) {
  return (
    <div className="field">
      {label && (
        <label className="field-label" htmlFor={htmlFor}>
          {label}
        </label>
      )}
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </div>
  )
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  hint?: string
  error?: string
}

export function TextField({ label, hint, error, id, className, ...rest }: TextFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  return (
    <FieldWrapper label={label} hint={hint} error={error} htmlFor={inputId}>
      <input id={inputId} className={['input', error ? 'input-error' : '', className ?? ''].filter(Boolean).join(' ')} {...rest} />
    </FieldWrapper>
  )
}

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  hint?: string
  error?: string
}

export function TextAreaField({ label, hint, error, id, className, ...rest }: TextAreaFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  return (
    <FieldWrapper label={label} hint={hint} error={error} htmlFor={inputId}>
      <textarea id={inputId} className={['textarea', error ? 'textarea-error' : '', className ?? ''].filter(Boolean).join(' ')} {...rest} />
    </FieldWrapper>
  )
}
