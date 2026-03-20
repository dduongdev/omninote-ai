import { User, Bot } from 'lucide-react'
import { useState } from 'react'
import type { CitationResponse, MessageResponse } from '../../types'
import { CitationViewer } from './CitationViewer'

interface Props {
  msg: MessageResponse
  conversationId: number
}

export function MessageItem({ msg, conversationId }: Props) {
  const [activeCitation, setActiveCitation] = useState<CitationResponse | null>(null)

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })

  // Build a map  index (1-based) → CitationResponse
  const citationMap = new Map<number, CitationResponse>()
  msg.citationsResponses?.forEach((c, i) => citationMap.set(i + 1, c))

  // Parse contentAnswer: replace [N] with clickable badge
  function renderAnswer(text: string) {
    const parts = text.split(/(\[\d+\])/g)
    return parts.map((part, i) => {
      const match = part.match(/^\[(\d+)\]$/)
      if (!match) return <span key={i}>{part}</span>
      const idx = parseInt(match[1], 10)
      const citation = citationMap.get(idx)
      if (!citation) return <span key={i} className="text-gray-500">{part}</span>
      return (
        <button
          key={i}
          onClick={() => setActiveCitation(citation)}
          className="inline-flex items-center justify-center w-5 h-5 rounded-full text-[10px] font-bold
                     bg-brand/30 text-brand-light border border-brand/40 hover:bg-brand/50
                     transition-colors mx-0.5 align-middle leading-none"
          title={`Xem trích dẫn từ "${citation.fileName}"`}
        >
          {idx}
        </button>
      )
    })
  }

  return (
    <>
      <div className="space-y-3">
        {/* Query */}
        <div className="flex items-start gap-3 justify-end">
          <div className="max-w-[75%]">
            <div className="bg-brand/20 border border-brand/30 text-gray-100 text-sm px-4 py-3 rounded-2xl rounded-tr-sm">
              {msg.contentQuery}
            </div>
            <p className="text-xs text-gray-600 mt-1 text-right">{formatTime(msg.createdAt)}</p>
          </div>
          <div className="w-8 h-8 bg-brand/20 rounded-full flex items-center justify-center shrink-0">
            <User className="w-4 h-4 text-brand-light" />
          </div>
        </div>

        {/* Answer */}
        <div className="flex items-start gap-3">
          <div className="w-8 h-8 bg-surface-overlay rounded-full flex items-center justify-center shrink-0 border border-white/10">
            <Bot className="w-4 h-4 text-gray-300" />
          </div>
          <div className="max-w-[85%] space-y-2">
            <div className="bg-surface-overlay border border-white/5 text-gray-200 text-sm px-4 py-3 rounded-2xl rounded-tl-sm leading-relaxed whitespace-pre-wrap">
              {renderAnswer(msg.contentAnswer)}
            </div>

            {/* Citation chips below the answer */}
            {msg.citationsResponses?.length > 0 && (
              <div className="flex flex-wrap gap-1 pt-1">
                {msg.citationsResponses.map((c, i) => (
                  <button
                    key={c.id}
                    onClick={() => setActiveCitation(c)}
                    className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded-full
                               bg-surface border border-white/10 text-gray-400
                               hover:border-brand/40 hover:text-brand-light transition-colors"
                    title="Xem đoạn trích dẫn"
                  >
                    <span className="w-4 h-4 rounded-full bg-brand/20 text-brand-light text-[9px] font-bold
                                     flex items-center justify-center shrink-0">
                      {i + 1}
                    </span>
                    <span className="max-w-[160px] truncate">{c.fileName}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {activeCitation && (
        <CitationViewer
          citation={activeCitation}
          conversationId={conversationId}
          onClose={() => setActiveCitation(null)}
        />
      )}
    </>
  )
}
