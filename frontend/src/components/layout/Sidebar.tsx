import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BookOpen, Plus, LogOut, MessageSquare, FileText, Trash2 } from 'lucide-react'
import { getConversations } from '../../api/conversations'
import { useAuthStore } from '../../store/authStore'
import { CreateConversationModal } from '../conversation/CreateConversationModal'
import { Spinner } from '../ui/Spinner'
import type { ConversationSummary } from '../../types'

interface Props {
  selectedId: number | null
  onSelect: (id: number) => void
}

export function Sidebar({ selectedId, onSelect }: Props) {
  const [showCreate, setShowCreate] = useState(false)
  const logout = useAuthStore((s) => s.logout)

  const { data: conversations = [], isLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: getConversations,
    refetchInterval: 10000,
  })

  return (
    <>
      <aside className="w-72 bg-surface-raised border-r border-white/5 flex flex-col h-screen shrink-0">
        {/* Header */}
        <div className="p-4 border-b border-white/5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-9 h-9 bg-brand/20 rounded-xl flex items-center justify-center">
              <BookOpen className="w-5 h-5 text-brand-light" />
            </div>
            <div>
              <h1 className="font-bold text-white text-sm">OmniNote AI</h1>
              <p className="text-xs text-gray-500">Trợ lý nghiên cứu</p>
            </div>
          </div>

          <button
            onClick={() => setShowCreate(true)}
            className="btn-primary w-full flex items-center justify-center gap-2 py-2 text-sm"
          >
            <Plus className="w-4 h-4" />
            Hội thoại mới
          </button>
        </div>

        {/* List */}
        <div className="flex-1 overflow-y-auto p-2">
          {isLoading ? (
            <div className="flex justify-center py-8"><Spinner /></div>
          ) : conversations.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              <MessageSquare className="w-8 h-8 mx-auto mb-2 opacity-40" />
              <p className="text-sm">Chưa có hội thoại nào</p>
            </div>
          ) : (
            conversations.map((c) => (
              <ConversationItem
                key={c.id}
                conversation={c}
                selected={c.id === selectedId}
                onClick={() => onSelect(c.id)}
              />
            ))
          )}
        </div>

        {/* Footer */}
        <div className="p-3 border-t border-white/5">
          <button
            onClick={logout}
            className="btn-ghost w-full flex items-center gap-2 text-sm text-gray-400"
          >
            <LogOut className="w-4 h-4" />
            Đăng xuất
          </button>
        </div>
      </aside>

      {showCreate && (
        <CreateConversationModal
          onClose={() => setShowCreate(false)}
          onSuccess={(id) => { setShowCreate(false); onSelect(id) }}
        />
      )}
    </>
  )
}

function ConversationItem({
  conversation: c,
  selected,
  onClick,
}: {
  conversation: ConversationSummary
  selected: boolean
  onClick: () => void
}) {
  const isDeleting = c.status === 'DELETING' || c.status === 'DELETE_FAILED'

  return (
    <button
      onClick={onClick}
      className={`w-full text-left px-3 py-3 rounded-xl mb-1 transition-all group ${
        selected
          ? 'bg-brand/15 border border-brand/30'
          : 'hover:bg-white/5 border border-transparent'
      }`}
    >
      <div className="flex items-start gap-2">
        <div className={`mt-0.5 shrink-0 ${selected ? 'text-brand-light' : 'text-gray-500'}`}>
          <MessageSquare className="w-4 h-4" />
        </div>
        <div className="flex-1 min-w-0">
          <p className={`text-sm font-medium truncate ${selected ? 'text-white' : 'text-gray-300'}`}>
            {c.title}
          </p>
          <div className="flex items-center gap-3 mt-1 text-xs text-gray-500">
            <span className="flex items-center gap-1">
              <FileText className="w-3 h-3" /> {c.documentCount}
            </span>
            <span className="flex items-center gap-1">
              <MessageSquare className="w-3 h-3" /> {c.messageCount}
            </span>
            {isDeleting && (
              <span className="text-orange-400 flex items-center gap-1">
                <Trash2 className="w-3 h-3" /> Đang xóa
              </span>
            )}
          </div>
        </div>
      </div>
    </button>
  )
}
