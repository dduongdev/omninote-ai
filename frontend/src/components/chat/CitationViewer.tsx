import { X, FileText, AlertTriangle, Loader2 } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getDocumentContent } from '../../api/documents'
import type { CitationResponse } from '../../types'

interface Props {
  citation: CitationResponse
  conversationId: number
  onClose: () => void
}

export function CitationViewer({ citation, conversationId, onClose }: Props) {
  const highlightRef = useRef<HTMLSpanElement>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['doc-content', conversationId, citation.documentId],
    queryFn: () => getDocumentContent(conversationId, citation.documentId),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  // scroll highlight into view once content loads
  useEffect(() => {
    if (data && highlightRef.current) {
      highlightRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }, [data])

  const is404 =
    isError && (error as { response?: { status?: number } })?.response?.status === 404

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="bg-surface-elevated border border-white/10 rounded-2xl w-full max-w-2xl max-h-[80vh] flex flex-col shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/10 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <FileText className="w-4 h-4 text-brand-light shrink-0" />
            <span className="text-sm font-medium text-gray-200 truncate">
              {citation.fileName}
            </span>
          </div>
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-gray-200 transition-colors ml-4 shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {isLoading && (
            <div className="flex flex-col items-center justify-center h-40 gap-3 text-gray-500">
              <Loader2 className="w-6 h-6 animate-spin" />
              <span className="text-sm">Đang tải nội dung...</span>
            </div>
          )}

          {is404 && (
            <div className="flex flex-col items-center justify-center h-40 gap-3">
              <AlertTriangle className="w-8 h-8 text-amber-500" />
              <p className="text-sm text-gray-400 text-center">
                Tài liệu này đã bị xóa hoặc không còn tồn tại.
              </p>
            </div>
          )}

          {isError && !is404 && (
            <div className="flex flex-col items-center justify-center h-40 gap-3">
              <AlertTriangle className="w-8 h-8 text-red-500" />
              <p className="text-sm text-gray-400 text-center">Không thể tải nội dung tài liệu.</p>
            </div>
          )}

          {data && (
            <ContentWithHighlight
              content={data.content}
              startIndex={citation.startIndex}
              endIndex={citation.endIndex}
              highlightRef={highlightRef}
            />
          )}
        </div>

        {/* Footer badge */}
        {data && (
          <div className="px-5 py-3 border-t border-white/10 shrink-0">
            <span className="text-xs text-gray-600">
              Trích dẫn: ký tự {citation.startIndex} – {citation.endIndex}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}

function ContentWithHighlight({
  content,
  startIndex,
  endIndex,
  highlightRef,
}: {
  content: string
  startIndex: number
  endIndex: number
  highlightRef: React.RefObject<HTMLSpanElement | null>
}) {
  const safeStart = Math.max(0, Math.min(startIndex, content.length))
  const safeEnd = Math.max(safeStart, Math.min(endIndex, content.length))

  const before = content.slice(0, safeStart)
  const highlighted = content.slice(safeStart, safeEnd)
  const after = content.slice(safeEnd)

  return (
    <p className="text-sm text-gray-300 leading-relaxed whitespace-pre-wrap font-mono">
      {before}
      <span
        ref={highlightRef}
        className="bg-amber-400/25 text-amber-200 rounded px-0.5 ring-1 ring-amber-400/40"
      >
        {highlighted}
      </span>
      {after}
    </p>
  )
}
