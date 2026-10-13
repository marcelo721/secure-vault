import { Loader2, ShieldCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { FormField } from '../components/FormField'

type Errors = Partial<Record<'name' | 'email' | 'password' | 'confirm', string>>

export function RegisterPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to="/" replace />
  }

  function validate(): boolean {
    const next: Errors = {}
    if (!name.trim()) next.name = 'Informe o nome.'
    if (!email.trim()) next.email = 'Informe o e-mail.'
    else if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = 'Informe um e-mail válido.'
    if (password.length < 10) next.password = 'A senha deve ter pelo menos 10 caracteres.'
    else if (password.length > 72) next.password = 'A senha deve ter no máximo 72 caracteres.'
    if (confirm !== password) next.confirm = 'As senhas não coincidem.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return
    setSubmitting(true)
    try {
      await api.register(name.trim(), email.trim(), password)
      navigate('/login', { replace: true, state: { registered: true } })
    } catch (error) {
      if (error instanceof ApiRequestError) {
        const fromServer: Errors = {}
        for (const violation of error.fieldErrors) {
          if (violation.field === 'name' || violation.field === 'email' || violation.field === 'password') {
            fromServer[violation.field] = violation.message
          }
        }
        setErrors(fromServer)
        setFormError(error.fieldErrors.length > 0 ? null : error.message)
      } else {
        setFormError('Não foi possível criar a conta. Tente novamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-8">
      <div className="card w-full max-w-md p-8">
        <div className="mb-6 flex flex-col items-center text-center">
          <ShieldCheck className="h-10 w-10 text-emerald-500" aria-hidden />
          <h1 className="mt-3 text-2xl font-semibold">Criar conta</h1>
          <p className="mt-1 text-sm text-slate-400">Guarde seus segredos em um só lugar.</p>
        </div>

        {formError && (
          <p className="mb-4 rounded-lg border border-red-700 bg-red-950 px-3 py-2 text-sm text-red-100" role="alert">
            {formError}
          </p>
        )}

        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <FormField id="name" label="Nome" error={errors.name}>
            <input
              id="name"
              type="text"
              autoComplete="name"
              className={`input ${errors.name ? 'input-error' : ''}`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={100}
              aria-invalid={!!errors.name}
              aria-describedby={errors.name ? 'name-error' : undefined}
              disabled={submitting}
            />
          </FormField>
          <FormField id="email" label="E-mail" error={errors.email}>
            <input
              id="email"
              type="email"
              autoComplete="email"
              className={`input ${errors.email ? 'input-error' : ''}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              maxLength={254}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              disabled={submitting}
            />
          </FormField>
          <FormField id="password" label="Senha" error={errors.password} hint="Entre 10 e 72 caracteres.">
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              className={`input ${errors.password ? 'input-error' : ''}`}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              maxLength={72}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
              disabled={submitting}
            />
          </FormField>
          <FormField id="confirm" label="Confirmar senha" error={errors.confirm}>
            <input
              id="confirm"
              type="password"
              autoComplete="new-password"
              className={`input ${errors.confirm ? 'input-error' : ''}`}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              maxLength={72}
              aria-invalid={!!errors.confirm}
              aria-describedby={errors.confirm ? 'confirm-error' : undefined}
              disabled={submitting}
            />
          </FormField>
          <button type="submit" className="btn btn-primary w-full" disabled={submitting}>
            {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            Criar conta
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-400">
          Já tem conta?{' '}
          <Link to="/login" className="text-emerald-400 hover:underline">
            Entrar
          </Link>
        </p>
      </div>
    </div>
  )
}
