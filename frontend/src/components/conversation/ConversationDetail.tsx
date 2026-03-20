import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2, AlertTriangle } from 'lucide-react'
import { getConversationHistory, deleteConversation } from '../../api/conversations'
import { DocumentPanel } from '../document/DocumentPanel'
import { ChatPanel } from '../chat/ChatPanel'
import { Spinner } from '../ui/Spinner'
import { Modal } from '../ui/Modal'
import type { AxiosError } from 'axios'

interface Props {
  conversationId: number
  onDeleted: () => void
}

export function ConversationDetail({ conversationId, onDeleted }: Props) {
  const [selectedDocIds, setSelectedDocIds] = useState<number[]>([])
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const qc = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['conversation', conversationId],
    queryFn: () => getConversationHistory(conversationId),
    refetchInterval: (query) => {
      const docs = query.state.data?.documents ?? []
      const hasProcessing = docs.some((d) => d.status === 'PROCESSING' || d.status === 'DELETING')
      return hasProcessing ? 3000 : false
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => deleteConversation(conversationId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      onDeleted()
    },
    onError: (e: AxiosError<{ detail: string }>) => {
      setDeleteError(e.response?.data?.detail || 'Xóa thất bại')
    },
  })

  const toggleDoc = (id: number) => {
    setSelectedDocIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    )
  }

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Spinner size="lg" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center text-red-400 gap-2">
        <AlertTriangle className="w-5 h-5" />
        Không thể tải hội thoại
      </div>
    )
  }

  if (!data) return null

  const isDeleting = data.status === 'DELETING' || data.status === 'DELETE_FAILED'

  return (
    <>
      <div className="flex flex-col flex-1 min-w-0 h-screen">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-white/5 shrink-0">
          <div>
            <h2 className="font-semibold text-white">{data.title}</h2>
            {isDeleting && (
              <p className="text-xs text-orange-400 mt-0.5">Đang trong quá trình xóa...</p>
            )}
          </div>
          <button
            onClick={() => setShowDeleteConfirm(true)}
            disabled={isDeleting}
            className="btn-danger text-sm flex items-center gap-1.5 disabled:opacity-40"
          >
            <Trash2 className="w-4 h-4" />
            Xóa hội thoại
          </button>
        </div>

        {/* Body: Documents | Chat */}
        <div className="flex flex-1 overflow-hidden">
          <DocumentPanel
            conversationId={conversationId}
            documents={data.documents}
            selectedDocIds={selectedDocIds}
            onToggleDoc={toggleDoc}
          />
          <div className="flex-1 overflow-hidden">
            <ChatPanel
              conversationId={conversationId}
              messages={data.messages}
              selectedDocIds={selectedDocIds}
            />
          </div>
        </div>
      </div>

      {/* Delete confirmation */}
      {showDeleteConfirm && (
        <Modal title="Xác nhận xóa hội thoại" onClose={() => setShowDeleteConfirm(false)} size="sm">
          <div className="space-y-4">
            <p className="text-sm text-gray-300">
              Bạn có chắc muốn xóa hội thoại <span className="font-semibold text-white">"{data.title}"</span>?
              Tất cả tin nhắn và tài liệu sẽ bị xóa vĩnh viễn.
            </p>
            {deleteError && (
              <div className="bg-red-500/10 border border-red-500/30 text-red-300 text-sm px-3 py-2 rounded-lg">
                {deleteError}
              </div>
            )}
            <div className="flex gap-2">
              <button onClick={() => setShowDeleteConfirm(false)} className="btn-ghost flex-1">
                Huỷ
              </button>
              <button
                onClick={() => deleteMutation.mutate()}
                disabled={deleteMutation.isPending}
                className="flex-1 bg-red-600 hover:bg-red-700 text-white font-medium px-4 py-2 rounded-lg transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {deleteMutation.isPending && <Spinner size="sm" />}
                Xóa
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  )
}
