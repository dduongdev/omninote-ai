import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2, AlertTriangle, Edit2, Check, X } from 'lucide-react'
import { getConversationHistory, deleteConversation, updateConversationName } from '../../api/conversations'
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
  const [selectedDocIds, setSelectedDocIds] = useState<number[] | null>(null)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const [isEditingName, setIsEditingName] = useState(false)
  const [editName, setEditName] = useState('')
  const qc = useQueryClient()
  
  // Track seen documents to automatically select newly processed docs
  const seenDocsRef = useRef<Set<number>>(new Set())

  const { data, isLoading, error } = useQuery({
    queryKey: ['conversation', conversationId],
    queryFn: () => getConversationHistory(conversationId),
    refetchInterval: (query) => {
      const docs = query.state.data?.documents ?? []
      const hasProcessing = docs.some((d) => d.status === 'PROCESSING' || d.status === 'DELETING')
      return hasProcessing ? 3000 : false
    },
  })

  useEffect(() => {
    if (data?.documents) {
      const readyIds = data.documents.filter(d => d.status === 'READY').map(d => d.id)
      
      if (selectedDocIds === null) {
        // Initial load: select all ready docs
        setSelectedDocIds(readyIds)
        seenDocsRef.current = new Set(readyIds)
      } else {
        // Auto-select newly processed docs
        const newReadyIds = readyIds.filter(id => !seenDocsRef.current.has(id))
        if (newReadyIds.length > 0) {
          setSelectedDocIds(prev => [...(prev || []), ...newReadyIds])
          newReadyIds.forEach(id => seenDocsRef.current.add(id))
        }
      }
    }
  }, [data?.documents, selectedDocIds])

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

  const updateNameMutation = useMutation({
    mutationFn: (newName: string) => updateConversationName(conversationId, newName),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['conversation', conversationId] })
      qc.invalidateQueries({ queryKey: ['conversations'] })
      setIsEditingName(false)
    },
  })

  const handleUpdateName = () => {
    if (editName.trim() && editName.trim() !== data?.title) {
      updateNameMutation.mutate(editName.trim())
    } else {
      setIsEditingName(false)
    }
  }

  const toggleDoc = (id: number) => {
    setSelectedDocIds((prev) => {
      const current = prev || []
      return current.includes(id) ? current.filter((x) => x !== id) : [...current, id]
    })
  }

  const toggleAllDocs = (ids: number[], forceSelect: boolean) => {
    if (forceSelect) {
      setSelectedDocIds(ids)
    } else {
      setSelectedDocIds([])
    }
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
  const currentSelectedIds = selectedDocIds || []

  return (
    <>
      <div className="flex flex-col flex-1 min-w-0 h-screen">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-white/5 shrink-0">
          <div className="flex-1 max-w-xl">
            {isEditingName ? (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleUpdateName()}
                  className="bg-black/20 border border-white/10 rounded-lg px-3 py-1 text-sm text-white focus:outline-none focus:border-brand-500 w-full"
                  autoFocus
                />
                <button 
                  onClick={handleUpdateName}
                  disabled={updateNameMutation.isPending || !editName.trim()}
                  className="p-1.5 bg-brand-600/20 text-brand-light rounded-lg hover:bg-brand-600/30 transition-colors disabled:opacity-50"
                >
                  {updateNameMutation.isPending ? <Spinner size="sm" /> : <Check className="w-4 h-4" />}
                </button>
                <button
                  onClick={() => setIsEditingName(false)}
                  className="p-1.5 bg-white/5 text-gray-400 rounded-lg hover:bg-white/10 transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-3 group">
                <h2 className="font-semibold text-white truncate">{data.title}</h2>
                <button
                  onClick={() => {
                    setEditName(data.title)
                    setIsEditingName(true)
                  }}
                  className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-white transition-all rounded"
                  title="Sửa tên hội thoại"
                >
                  <Edit2 className="w-3.5 h-3.5" />
                </button>
              </div>
            )}
            {isDeleting && (
              <p className="text-xs text-orange-400 mt-0.5">Đang trong quá trình xóa...</p>
            )}
          </div>
          <button
            onClick={() => setShowDeleteConfirm(true)}
            disabled={isDeleting}
            className="btn-danger text-sm flex items-center gap-1.5 disabled:opacity-40 ml-4"
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
            selectedDocIds={currentSelectedIds}
            onToggleDoc={toggleDoc}
            onToggleAllDocs={toggleAllDocs}
          />
          <div className="flex-1 overflow-hidden">
            <ChatPanel
              conversationId={conversationId}
              messages={data.messages}
              selectedDocIds={currentSelectedIds}
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
