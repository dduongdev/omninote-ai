import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FileText, Trash2, Plus, CheckSquare, Square } from 'lucide-react'
import { deleteDocument } from '../../api/documents'
import { DocumentStatusBadge } from '../ui/Badge'
import { UploadDocumentModal } from './UploadDocumentModal'
import type { DocumentSummary } from '../../types'

interface Props {
  conversationId: number
  documents: DocumentSummary[]
  selectedDocIds: number[]
  onToggleDoc: (id: number) => void
}

export function DocumentPanel({ conversationId, documents, selectedDocIds, onToggleDoc }: Props) {
  const [showUpload, setShowUpload] = useState(false)
  const qc = useQueryClient()

  const deleteMutation = useMutation({
    mutationFn: (docId: number) => deleteDocument(conversationId, docId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['conversation', conversationId] }),
  })

  const readyDocs = documents.filter((d) => d.status === 'READY')

  return (
    <>
      <div className="w-64 border-r border-white/5 flex flex-col h-full shrink-0">
        <div className="p-3 border-b border-white/5 flex items-center justify-between">
          <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
            Tài liệu ({documents.length}/5)
          </span>
          {documents.length < 5 && (
            <button
              onClick={() => setShowUpload(true)}
              className="p-1 rounded-lg hover:bg-white/10 text-gray-400 hover:text-white transition-colors"
              title="Thêm tài liệu"
            >
              <Plus className="w-4 h-4" />
            </button>
          )}
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {documents.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              <FileText className="w-7 h-7 mx-auto mb-2 opacity-30" />
              <p className="text-xs">Chưa có tài liệu</p>
            </div>
          )}

          {documents.map((doc) => {
            const isReady = doc.status === 'READY'
            const isSelected = selectedDocIds.includes(doc.id)
            const isDeleting = doc.status === 'DELETING' || doc.status === 'DELETE_FAILED'

            return (
              <div
                key={doc.id}
                className={`group flex items-start gap-2 p-2 rounded-lg transition-colors ${
                  isReady ? 'hover:bg-white/5 cursor-pointer' : 'opacity-60'
                } ${isSelected ? 'bg-brand/10' : ''}`}
                onClick={() => isReady && onToggleDoc(doc.id)}
              >
                {/* Checkbox */}
                <div className={`mt-0.5 shrink-0 ${isSelected ? 'text-brand-light' : 'text-gray-600'}`}>
                  {isSelected ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
                </div>

                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-gray-300 truncate" title={doc.fileName}>
                    {doc.fileName}
                  </p>
                  <div className="mt-1">
                    <DocumentStatusBadge status={doc.status} />
                  </div>
                </div>

                {/* Delete button */}
                {isReady && !isDeleting && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      deleteMutation.mutate(doc.id)
                    }}
                    disabled={deleteMutation.isPending}
                    className="opacity-0 group-hover:opacity-100 text-gray-500 hover:text-red-400 transition-all p-0.5 shrink-0"
                    title="Xóa tài liệu"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            )
          })}
        </div>

        {readyDocs.length > 0 && (
          <div className="p-2 border-t border-white/5">
            <p className="text-xs text-gray-500 text-center">
              {selectedDocIds.length > 0
                ? `${selectedDocIds.length} tài liệu được chọn để hỏi`
                : 'Chọn tài liệu để hỏi'}
            </p>
          </div>
        )}
      </div>

      {showUpload && (
        <UploadDocumentModal
          conversationId={conversationId}
          currentDocCount={documents.length}
          onClose={() => setShowUpload(false)}
        />
      )}
    </>
  )
}
