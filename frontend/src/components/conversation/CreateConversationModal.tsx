import { useState, useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, X, FileText } from 'lucide-react'
import { createConversation } from '../../api/conversations'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'
import type { AxiosError } from 'axios'

interface Props {
  onClose: () => void
  onSuccess: (id: number) => void
}

export function CreateConversationModal({ onClose, onSuccess }: Props) {
  const [title, setTitle] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [error, setError] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)
  const qc = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      if (title.trim()) fd.append('title', title.trim())
      files.forEach((f) => fd.append('files', f))
      return createConversation(fd)
    },
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['conversations'] })
      onSuccess(data.id)
    },
    onError: (e: AxiosError<{ detail: string }>) => {
      setError(e.response?.data?.detail || 'Tạo hội thoại thất bại')
    },
  })

  const addFiles = (incoming: FileList | null) => {
    if (!incoming) return
    const valid = Array.from(incoming).filter((f) =>
      f.type === 'application/pdf' || f.type === 'text/plain'
    )
    const merged = [...files, ...valid].slice(0, 5)
    setFiles(merged)
  }

  const removeFile = (i: number) => setFiles(files.filter((_, j) => j !== i))

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    addFiles(e.dataTransfer.files)
  }

  return (
    <Modal title="Tạo hội thoại mới" onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-sm text-gray-400 mb-1">Tiêu đề (tuỳ chọn)</label>
          <input
            className="input-field"
            placeholder="VD: Nghiên cứu về AI..."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        {/* Drop zone */}
        <div>
          <label className="block text-sm text-gray-400 mb-1">
            Tài liệu <span className="text-gray-500">(PDF / TXT, tối đa 5 file)</span>
          </label>
          <div
            onDrop={handleDrop}
            onDragOver={(e) => e.preventDefault()}
            onClick={() => fileRef.current?.click()}
            className="border-2 border-dashed border-white/10 hover:border-brand/40 rounded-xl p-6 text-center cursor-pointer transition-colors"
          >
            <Upload className="w-8 h-8 text-gray-500 mx-auto mb-2" />
            <p className="text-sm text-gray-400">Kéo thả hoặc <span className="text-brand-light">chọn file</span></p>
            <input
              ref={fileRef}
              type="file"
              accept=".pdf,.txt"
              multiple
              className="hidden"
              onChange={(e) => addFiles(e.target.files)}
            />
          </div>
        </div>

        {files.length > 0 && (
          <ul className="space-y-2 max-h-40 overflow-y-auto">
            {files.map((f, i) => (
              <li key={i} className="flex items-center gap-2 bg-surface px-3 py-2 rounded-lg text-sm">
                <FileText className="w-4 h-4 text-brand-light shrink-0" />
                <span className="flex-1 truncate text-gray-300">{f.name}</span>
                <span className="text-gray-500 text-xs">{(f.size / 1024).toFixed(0)} KB</span>
                <button onClick={() => removeFile(i)} className="text-gray-500 hover:text-red-400 transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </li>
            ))}
          </ul>
        )}

        {error && (
          <div className="bg-red-500/10 border border-red-500/30 text-red-300 text-sm px-3 py-2 rounded-lg">
            {error}
          </div>
        )}

        <div className="flex gap-2 pt-1">
          <button onClick={onClose} className="btn-ghost flex-1">Huỷ</button>
          <button
            onClick={() => { setError(''); mutation.mutate() }}
            disabled={files.length === 0 || mutation.isPending}
            className="btn-primary flex-1 flex items-center justify-center gap-2"
          >
            {mutation.isPending && <Spinner size="sm" />}
            Tạo hội thoại
          </button>
        </div>
      </div>
    </Modal>
  )
}
