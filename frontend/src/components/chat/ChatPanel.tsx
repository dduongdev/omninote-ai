import { useState, useRef, useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Send, AlertCircle } from 'lucide-react'
import { sendMessage } from '../../api/chat'
import { MessageItem } from './MessageItem'
import { Spinner } from '../ui/Spinner'
import type { MessageResponse } from '../../types'

interface Props {
  conversationId: number
  messages: MessageResponse[]
  selectedDocIds: number[]
}

export function ChatPanel({ conversationId, messages, selectedDocIds }: Props) {
  const [input, setInput] = useState('')
  const [localMessages, setLocalMessages] = useState<MessageResponse[]>(messages)
  const [error, setError] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const qc = useQueryClient()

  // Sync messages from server
  useEffect(() => { setLocalMessages(messages) }, [messages])

  // Auto scroll
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [localMessages])

  const mutation = useMutation({
    mutationFn: () =>
      sendMessage({ conversationId, contentQuery: input.trim(), documentIds: selectedDocIds }),
    onMutate: () => setError(''),
    onSuccess: (msg) => {
      setLocalMessages((prev) => [...prev, msg])
      setInput('')
      qc.invalidateQueries({ queryKey: ['conversation', conversationId] })
    },
    onError: () => setError('Gửi tin nhắn thất bại. Thử lại sau.'),
  })

  const handleSend = () => {
    if (!input.trim() || mutation.isPending) return
    if (selectedDocIds.length === 0) {
      setError('Vui lòng chọn ít nhất 1 tài liệu để hỏi')
      return
    }
    mutation.mutate()
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {localMessages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-center pb-8">
            <div className="w-16 h-16 bg-brand/10 rounded-2xl flex items-center justify-center mb-4">
              <Send className="w-7 h-7 text-brand-light" />
            </div>
            <h3 className="text-gray-300 font-medium mb-1">Bắt đầu hội thoại</h3>
            <p className="text-gray-500 text-sm max-w-xs">
              Chọn tài liệu ở bên trái, sau đó đặt câu hỏi bên dưới
            </p>
          </div>
        )}

        {localMessages.map((msg) => (
          <MessageItem key={msg.id} msg={msg} conversationId={conversationId} />
        ))}

        {/* Typing indicator */}
        {mutation.isPending && (
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 bg-surface-overlay rounded-full flex items-center justify-center border border-white/10">
              <div className="flex gap-1">
                <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
              </div>
            </div>
            <div className="bg-surface-overlay border border-white/5 rounded-2xl rounded-tl-sm px-4 py-3 text-sm text-gray-400">
              Đang phân tích tài liệu...
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="p-4 border-t border-white/5">
        {error && (
          <div className="flex items-center gap-2 bg-red-500/10 border border-red-500/30 text-red-300 text-sm px-3 py-2 rounded-lg mb-3">
            <AlertCircle className="w-4 h-4 shrink-0" />
            {error}
          </div>
        )}

        {selectedDocIds.length === 0 && (
          <p className="text-xs text-gray-500 mb-2 text-center">
            ← Chọn tài liệu để bắt đầu hỏi
          </p>
        )}

        <div className="flex gap-2 items-end bg-surface-overlay border border-white/10 rounded-xl p-2 focus-within:border-brand/50 transition-colors">
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Đặt câu hỏi về tài liệu... (Enter để gửi)"
            rows={1}
            className="flex-1 bg-transparent text-gray-100 placeholder-gray-500 text-sm resize-none focus:outline-none max-h-32 py-1 px-1"
            style={{ height: 'auto' }}
            onInput={(e) => {
              const el = e.currentTarget
              el.style.height = 'auto'
              el.style.height = Math.min(el.scrollHeight, 128) + 'px'
            }}
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || mutation.isPending || selectedDocIds.length === 0}
            className="btn-primary px-3 py-2 shrink-0 flex items-center justify-center"
          >
            {mutation.isPending ? <Spinner size="sm" /> : <Send className="w-4 h-4" />}
          </button>
        </div>
      </div>
    </div>
  )
}
