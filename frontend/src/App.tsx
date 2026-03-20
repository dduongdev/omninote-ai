import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from './store/authStore'
import { AuthPage } from './components/auth/AuthPage'
import { AppLayout } from './components/layout/AppLayout'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 5000,
    },
  },
})

export default function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())

  return (
    <QueryClientProvider client={queryClient}>
      {isAuthenticated ? <AppLayout /> : <AuthPage />}
    </QueryClientProvider>
  )
}
