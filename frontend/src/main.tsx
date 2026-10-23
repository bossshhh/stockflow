import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'

import { ApiRequestError } from '@/api/errors'
import { AuthProvider } from '@/auth/AuthProvider'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { App } from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // Retrying a 4xx never helps (bad input, no permission, not found); retry network/5xx once.
      retry: (failureCount, error) =>
        failureCount < 1 && !(error instanceof ApiRequestError && error.status >= 400 && error.status < 500),
    },
  },
})

const root = document.getElementById('root')
if (root === null) {
  throw new Error('index.html is missing <div id="root">')
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <TooltipProvider delayDuration={300}>
            <App />
            <Toaster position="bottom-right" theme="dark" />
          </TooltipProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
