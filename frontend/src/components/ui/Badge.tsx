import type { DocumentStatus } from '../../types'

const config: Record<DocumentStatus, { label: string; className: string }> = {
  PROCESSING: { label: 'Đang xử lý', className: 'bg-yellow-500/20 text-yellow-300 border-yellow-500/30' },
  READY:      { label: 'Sẵn sàng',   className: 'bg-green-500/20 text-green-300 border-green-500/30' },
  FAILED:     { label: 'Lỗi',        className: 'bg-red-500/20 text-red-300 border-red-500/30' },
  DELETING:   { label: 'Đang xóa',   className: 'bg-orange-500/20 text-orange-300 border-orange-500/30' },
  DELETED:    { label: 'Đã xóa',     className: 'bg-gray-500/20 text-gray-400 border-gray-500/30' },
  DELETE_FAILED: { label: 'Xóa thất bại', className: 'bg-red-600/20 text-red-400 border-red-600/30' },
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  const c = config[status] ?? { label: status, className: 'bg-gray-500/20 text-gray-400 border-gray-500/30' }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border ${c.className}`}>
      {status === 'PROCESSING' && (
        <span className="w-1.5 h-1.5 rounded-full bg-yellow-400 animate-pulse mr-1.5" />
      )}
      {c.label}
    </span>
  )
}
