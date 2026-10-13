import { Loader2, ShieldCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { FormField } from '../components/FormField'

interface LocationState {
  from?: string
  expired?: boolean
  registered?: boolean
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const state = (useLocation().state ?? {}) as LocationState
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user && !submitting) {
    return <Navigate to="/" replace />
  }

  function validate() {
    const next: { email?: string; password?: string } = {}
    if (!email.trim()) next.email = 'Informe o e-mail.'
    else if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = 'Informe um e-mail válido.'
    if (!password) next.password = 'Informe a senha.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      const destination = state.from && state.from.startsWith('/') ? state.from : '/'
      navigate(destination, { replace: true })
    } catch (error) {
      setFormError(error instanceof ApiRequestError ? error.message : 'Não foi possível entrar. Tente novamente.')
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="card w-full max-w-md p-8">
        <div className="mb-6 flex flex-col items-center text-center">
          <ShieldCheck className="h-10 w-10 text-emerald-500" aria-hidden />
          <h1 className="mt-3 text-2xl font-semibold">Cofre Digital</h1>
          <p className="mt-1 text-sm text-slate-400">Entre para acessar seus registros.</p>
        </div>

        {state.registered && (
          <p className="mb-4 rounded-lg border border-emerald-700 bg-emerald-950 px-3 py-2 text-sm text-emerald-100" role="status">
            Conta criada com sucesso. Faça login para continuar.
          </p>
        )}
        {state.expired && (
          <p className="mb-4 rounded-lg border border-amber-700 bg-amber-950 px-3 py-2 text-sm text-amber-100" role="alert">
            Sua sessão expirou. Entre novamente.
          </p>
        )}
        {formError && (
          <p className="mb-4 rounded-lg border border-red-700 bg-red-950 px-3 py-2 text-sm text-red-100" role="alert">
            {formError}
          </p>
        )}

        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <FormField id="email" label="E-mail" error={errors.email}>
            <input
              id="email"
              type="email"
              autoComplete="username"
              className={`input ${errors.email ? 'input-error' : ''}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              maxLength={254}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              disabled={submitting}
            />
          </FormField>
          <FormField id="password" label="Senha" error={errors.password}>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              className={`input ${errors.password ? 'input-error' : ''}`}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              maxLength={72}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
              disabled={submitting}
            />
          </FormField>
          <button type="submit" className="btn btn-primary w-full" disabled={submitting}>
            {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            Entrar
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-400">
          Ainda não tem conta?{' '}
          <Link to="/register" className="text-emerald-400 hover:underline">
            Cadastre-se
          </Link>
        </p>
      </div>
    </div>
  )
}
