import { useState } from 'react'
import { BookOpen } from 'lucide-react'
import { Sidebar } from './Sidebar'
import { ConversationDetail } from '../conversation/ConversationDetail'

export function AppLayout() {
  const [selectedId, setSelectedId] = useState<number | null>(null)

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar selectedId={selectedId} onSelect={setSelectedId} />

      <main className="flex-1 flex overflow-hidden">
        {selectedId ? (
          <ConversationDetail
            conversationId={selectedId}
            onDeleted={() => setSelectedId(null)}
          />
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-center p-8">
            <div className="w-20 h-20 bg-brand/10 rounded-3xl flex items-center justify-center mb-6">
              <BookOpen className="w-10 h-10 text-brand-light" />
            </div>
            <h2 className="text-xl font-semibold text-gray-200 mb-2">Chào mừng đến OmniNote AI</h2>
            <p className="text-gray-500 max-w-sm text-sm leading-relaxed">
              Chọn một hội thoại từ danh sách bên trái, hoặc tạo hội thoại mới để bắt đầu
              đặt câu hỏi dựa trên tài liệu của bạn.
            </p>
          </div>
        )}
      </main>
    </div>
  )
}
