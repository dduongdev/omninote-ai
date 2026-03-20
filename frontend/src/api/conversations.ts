import api from './axios'
import type {
  ConversationSummary,
  ConversationHistoryResponse,
  ConversationCreateResponse,
  ConversationDeleteResponse,
} from '../types'

export const getConversations = async (): Promise<ConversationSummary[]> => {
  const res = await api.get('/api/v1/conversations')
  return res.data
}

export const getConversationHistory = async (id: number): Promise<ConversationHistoryResponse> => {
  const res = await api.get(`/api/v1/conversations/${id}/history`)
  return res.data
}

export const createConversation = async (formData: FormData): Promise<ConversationCreateResponse> => {
  const res = await api.post('/api/v1/conversations/create', formData)
  return res.data
}

export const deleteConversation = async (id: number): Promise<ConversationDeleteResponse> => {
  const res = await api.delete(`/api/v1/conversations/${id}`)
  return res.data
}
