import api from './axios'
import type { MessageResponse, SendMessageRequest } from '../types'

export const sendMessage = async (data: SendMessageRequest): Promise<MessageResponse> => {
  const res = await api.post('/api/v1/chat/sendmessages', data)
  return res.data
}
