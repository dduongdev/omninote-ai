import { useState, useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload, X, FileText } from 'lucide-react'
import { uploadDocuments } from '../../api/documents'
import { Modal } from '../ui/Modal'
import { Spinner } from '../ui/Spinner'
import type { AxiosError } from 'axios'

interface Props {
  conversationId: number
  currentDocCount: number
  onClose: () => void
}

export function UploadDocumentModal({ conversationId, currentDocCount, onClose }: Props) {
  const [files, setFiles] = useState<File[]>([])
  const [error, setError] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)
  const qc = useQueryClient()
  const maxNew = 5 - currentDocCount

  const mutation = useMutation({
    mutationFn: () => uploadDocuments(conversationId, files),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['conversation', conversationId] })
      onClose()
    },
    onError: (e: AxiosError<{ detail: string }>) => {
      setError(e.response?.data?.detail || 'Upload thất bại')
    },
  })

  const addFiles = (fl: FileList | null) => {
    if (!fl) return
    const valid = Array.from(fl).filter((f) => f.type === 'application/pdf' || f.type === 'text/plain')
    setFiles((prev) => [...prev, ...valid].slice(0, maxNew))
  }

  return (
    <Modal title="Tải thêm tài liệu" onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-gray-400">
          Còn có thể tải thêm <span className="text-white font-medium">{maxNew}</span> tài liệu
        </p>

        <div
          onClick={() => fileRef.current?.click()}
          onDrop={(e) => { e.preventDefault(); addFiles(e.dataTransfer.files) }}
          onDragOver={(e) => e.preventDefault()}
          className="border-2 border-dashed border-white/10 hover:border-brand/40 rounded-xl p-6 text-center cursor-pointer transition-colors"
        >
          <Upload className="w-7 h-7 text-gray-500 mx-auto mb-2" />
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

        {files.length > 0 && (
          <ul className="space-y-2 max-h-36 overflow-y-auto">
            {files.map((f, i) => (
              <li key={i} className="flex items-center gap-2 bg-surface px-3 py-2 rounded-lg text-sm">
                <FileText className="w-4 h-4 text-brand-light shrink-0" />
                <span className="flex-1 truncate text-gray-300">{f.name}</span>
                <button onClick={() => setFiles(files.filter((_, j) => j !== i))} className="text-gray-500 hover:text-red-400">
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

        <div className="flex gap-2">
          <button onClick={onClose} className="btn-ghost flex-1">Huỷ</button>
          <button
            onClick={() => { setError(''); mutation.mutate() }}
            disabled={files.length === 0 || mutation.isPending}
            className="btn-primary flex-1 flex items-center justify-center gap-2"
          >
            {mutation.isPending && <Spinner size="sm" />}
            Tải lên
          </button>
        </div>
      </div>
    </Modal>
  )
}
