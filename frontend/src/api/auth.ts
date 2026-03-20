import api from './axios'
import type { AuthRequest, AuthResponse, RegisterRequest } from '../types'

export const login = async (data: AuthRequest): Promise<AuthResponse> => {
  const res = await api.post('/api/auth/login', data)
  return res.data
}

export const register = async (data: RegisterRequest): Promise<AuthResponse> => {
  const res = await api.post('/api/auth/register', data)
  return res.data
}
