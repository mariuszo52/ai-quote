import { useEffect, useRef, type ReactNode } from 'react'

export interface ChatThreadMessage {
  role: string
  content: string
}

interface ChatThreadProps {
  messages: ChatThreadMessage[]
  assistantLabel?: string
  userLabel?: string
  typing?: boolean
  emptyHint?: ReactNode
  className?: string
}

function isAssistant(role: string) {
  return role === 'ASSISTANT'
}

function ChatThread({ messages, assistantLabel = 'AI', userLabel = 'Ty', typing = false, emptyHint, className }: ChatThreadProps) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, typing])

  return (
    <div className={['chat-thread', className ?? ''].filter(Boolean).join(' ')}>
      {messages.length === 0 && !typing && emptyHint}
      {messages.map((message, index) => {
        const assistant = isAssistant(message.role)
        return (
          <div key={index} className={`chat-row ${assistant ? 'chat-row-assistant' : 'chat-row-user'}`}>
            <span className="chat-row-label">{assistant ? assistantLabel : userLabel}</span>
            <div className={`chat-bubble ${assistant ? 'chat-bubble-assistant' : 'chat-bubble-user'}`}>
              {message.content || (assistant && index === messages.length - 1 ? <TypingDots /> : '')}
            </div>
          </div>
        )
      })}
      {typing && (
        <div className="chat-row chat-row-assistant">
          <span className="chat-row-label">{assistantLabel}</span>
          <div className="chat-bubble chat-bubble-assistant">
            <TypingDots />
          </div>
        </div>
      )}
      <div ref={bottomRef} />
    </div>
  )
}

function TypingDots() {
  return (
    <span className="chat-typing" aria-label="AI pisze...">
      <span />
      <span />
      <span />
    </span>
  )
}

export default ChatThread
