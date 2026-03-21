import api from './axios'
import type { DocumentContentResponse, DocumentSummary, DocumentUploadResponse } from '../types'

export const uploadDocuments = async (
  conversationId: number,
  files: File[]
): Promise<DocumentUploadResponse> => {
  const formData = new FormData()
  files.forEach((f) => formData.append('files', f))
  const res = await api.post(`/api/v1/conversations/${conversationId}/documents/upload`, formData, { timeout: 300000 })
  return res.data
}

export const deleteDocument = async (
  conversationId: number,
  documentId: number
): Promise<DocumentSummary> => {
  const res = await api.post(`/api/v1/conversations/${conversationId}/documents/delete`, {
    documentId,
  })
  return res.data
}

export const getDocumentContent = async (
  conversationId: number,
  documentId: number
): Promise<DocumentContentResponse> => {
  const res = await api.get(
    `/api/v1/conversations/${conversationId}/documents/${documentId}/content`
  )
  return res.data
}
