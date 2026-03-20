import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { BookOpen, Eye, EyeOff } from 'lucide-react'
import { login, register } from '../../api/auth'
import { useAuthStore } from '../../store/authStore'
import { Spinner } from '../ui/Spinner'
import type { AxiosError } from 'axios'

export function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const setToken = useAuthStore((s) => s.setToken)

  const [form, setForm] = useState({
    userName: '',
    password: '',
    email: '',
    name: '',
  })

  const loginMutation = useMutation({
    mutationFn: () => login({ userName: form.userName, password: form.password }),
    onSuccess: (data) => {
      if (data.token) setToken(data.token)
      else setError(data.message || 'Đăng nhập thất bại')
    },
    onError: (e: AxiosError<{ message: string }>) => {
      setError(e.response?.data?.message || 'Sai tên đăng nhập hoặc mật khẩu')
    },
  })

  const registerMutation = useMutation({
    mutationFn: () => register(form),
    onSuccess: (data) => {
      if (data.token) setToken(data.token)
      else setError(data.message || 'Đăng ký thất bại')
    },
    onError: (e: AxiosError<{ message: string }>) => {
      setError(e.response?.data?.message || 'Đăng ký thất bại')
    },
  })

  const loading = loginMutation.isPending || registerMutation.isPending

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (mode === 'login') loginMutation.mutate()
    else registerMutation.mutate()
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-brand/20 rounded-2xl mb-4">
            <BookOpen className="w-7 h-7 text-brand-light" />
          </div>
          <h1 className="text-2xl font-bold text-white">OmniNote AI</h1>
          <p className="text-gray-400 mt-1 text-sm">Trợ lý nghiên cứu thông minh</p>
        </div>

        <div className="card p-6 shadow-2xl">
          {/* Tab switch */}
          <div className="flex bg-surface rounded-lg p-1 mb-6">
            {(['login', 'register'] as const).map((m) => (
              <button
                key={m}
                onClick={() => { setMode(m); setError('') }}
                className={`flex-1 py-2 text-sm font-medium rounded-md transition-all ${
                  mode === m
                    ? 'bg-brand text-white shadow'
                    : 'text-gray-400 hover:text-white'
                }`}
              >
                {m === 'login' ? 'Đăng nhập' : 'Đăng ký'}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === 'register' && (
              <>
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Họ tên</label>
                  <input
                    className="input-field"
                    placeholder="Nguyễn Văn A"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm text-gray-400 mb-1">Email</label>
                  <input
                    type="email"
                    className="input-field"
                    placeholder="email@example.com"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    required
                  />
                </div>
              </>
            )}

            <div>
              <label className="block text-sm text-gray-400 mb-1">Tên đăng nhập</label>
              <input
                className="input-field"
                placeholder="username"
                value={form.userName}
                onChange={(e) => setForm({ ...form, userName: e.target.value })}
                required
              />
            </div>

            <div>
              <label className="block text-sm text-gray-400 mb-1">Mật khẩu</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="input-field pr-10"
                  placeholder="••••••••"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {error && (
              <div className="bg-red-500/10 border border-red-500/30 text-red-300 text-sm px-3 py-2 rounded-lg">
                {error}
              </div>
            )}

            <button type="submit" disabled={loading} className="btn-primary w-full flex items-center justify-center gap-2 py-2.5">
              {loading && <Spinner size="sm" />}
              {mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
