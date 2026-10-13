import { Loader2 } from 'lucide-react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Apenas conveniência de navegação: a autorização real é sempre feita pelo backend. */
export function ProtectedRoute() {
  const { user, loading, sessionExpired } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center text-slate-400" role="status">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" aria-hidden />
        Verificando sessão...
      </div>
    )
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname, expired: sessionExpired }} />
  }
  return <Outlet />
}
