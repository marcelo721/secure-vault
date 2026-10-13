import { ArrowLeft, Loader2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiRequestError } from '../api/client'
import { ITEM_TYPES, type VaultItemInput, type VaultItemType } from '../api/types'
import { FormField } from '../components/FormField'
import { useToast } from '../components/Toast'

type Errors = Partial<Record<keyof VaultItemInput, string>>

const EMPTY: VaultItemInput = { title: '', type: 'CREDENTIAL', username: '', secret: '', url: '', description: '' }

function validate(values: VaultItemInput): Errors {
  const errors: Errors = {}
  if (!values.title.trim()) errors.title = 'O título é obrigatório.'
  else if (values.title.length > 150) errors.title = 'O título deve ter no máximo 150 caracteres.'
  if (values.username.length > 255) errors.username = 'Máximo de 255 caracteres.'
  if (!values.secret.trim()) errors.secret = 'O segredo é obrigatório.'
  else if (values.secret.length > 10_000) errors.secret = 'O segredo deve ter no máximo 10.000 caracteres.'
  if (values.url.trim() && !/^https?:\/\/\S+$/.test(values.url.trim())) {
    errors.url = 'A URL deve começar com http:// ou https://.'
  } else if (values.url.length > 2048) errors.url = 'A URL deve ter no máximo 2048 caracteres.'
  if (values.description.length > 1000) errors.description = 'A descrição deve ter no máximo 1000 caracteres.'
  return errors
}

export function ItemFormPage() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const toast = useToast()
  const [values, setValues] = useState<VaultItemInput>(EMPTY)
  const [errors, setErrors] = useState<Errors>({})
  const [loading, setLoading] = useState(editing)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    let active = true
    api
      .getItem(id)
      .then((item) => {
        if (!active) return
        setValues({
          title: item.title,
          type: item.type,
          username: item.username ?? '',
          secret: item.secret,
          url: item.url ?? '',
          description: item.description ?? '',
        })
      })
      .catch((e: unknown) => {
        if (!active || (e instanceof ApiRequestError && e.status === 401)) return
        setLoadError(
          e instanceof ApiRequestError && e.status === 404
            ? 'Registro não encontrado.'
            : 'Não foi possível carregar o registro.',
        )
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [id])

  function set<K extends keyof VaultItemInput>(key: K, value: VaultItemInput[K]) {
    setValues((current) => ({ ...current, [key]: value }))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSubmitting(true)
    try {
      const saved = id ? await api.updateItem(id, values) : await api.createItem(values)
      toast.success(id ? 'Registro atualizado.' : 'Registro criado.')
      navigate(`/items/${saved.id}`, { replace: true })
    } catch (e) {
      if (e instanceof ApiRequestError) {
        const fromServer: Errors = {}
        for (const violation of e.fieldErrors) {
          if (violation.field in EMPTY) fromServer[violation.field as keyof VaultItemInput] = violation.message
        }
        setErrors(fromServer)
        setFormError(e.fieldErrors.length > 0 ? null : e.message)
      } else {
        setFormError('Não foi possível salvar o registro.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const isNote = values.type === 'SECURE_NOTE'

  return (
    <div className="mx-auto max-w-2xl">
      <Link to={id ? `/items/${id}` : '/'} className="mb-4 inline-flex items-center gap-1 text-sm text-slate-400 hover:text-slate-200">
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Voltar
      </Link>

      <div className="card p-6">
        <h1 className="text-2xl font-semibold">{editing ? 'Editar registro' : 'Novo registro'}</h1>

        {loading && (
          <div className="flex items-center justify-center py-12 text-slate-400" role="status">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" aria-hidden />
            Carregando...
          </div>
        )}

        {loadError && (
          <p className="mt-6 text-red-300" role="alert">
            {loadError}
          </p>
        )}

        {!loading && !loadError && (
          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
            {formError && (
              <p className="rounded-lg border border-red-700 bg-red-950 px-3 py-2 text-sm text-red-100" role="alert">
                {formError}
              </p>
            )}

            <FormField id="title" label="Título" error={errors.title}>
              <input
                id="title"
                type="text"
                className={`input ${errors.title ? 'input-error' : ''}`}
                value={values.title}
                onChange={(e) => set('title', e.target.value)}
                maxLength={150}
                aria-invalid={!!errors.title}
                aria-describedby={errors.title ? 'title-error' : undefined}
                disabled={submitting}
              />
            </FormField>

            <FormField id="type" label="Tipo">
              <select
                id="type"
                className="input"
                value={values.type}
                onChange={(e) => set('type', e.target.value as VaultItemType)}
                disabled={submitting}
              >
                {ITEM_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </FormField>

            <FormField id="username" label="Usuário / identificador (opcional)" error={errors.username}>
              <input
                id="username"
                type="text"
                autoComplete="off"
                className={`input ${errors.username ? 'input-error' : ''}`}
                value={values.username}
                onChange={(e) => set('username', e.target.value)}
                maxLength={255}
                aria-invalid={!!errors.username}
                aria-describedby={errors.username ? 'username-error' : undefined}
                disabled={submitting}
              />
            </FormField>

            <FormField id="secret" label={isNote ? 'Conteúdo da nota' : 'Segredo'} error={errors.secret}>
              {isNote ? (
                <textarea
                  id="secret"
                  rows={6}
                  autoComplete="off"
                  className={`input font-mono ${errors.secret ? 'input-error' : ''}`}
                  value={values.secret}
                  onChange={(e) => set('secret', e.target.value)}
                  maxLength={10_000}
                  aria-invalid={!!errors.secret}
                  aria-describedby={errors.secret ? 'secret-error' : undefined}
                  disabled={submitting}
                />
              ) : (
                <input
                  id="secret"
                  type="password"
                  autoComplete="new-password"
                  className={`input font-mono ${errors.secret ? 'input-error' : ''}`}
                  value={values.secret}
                  onChange={(e) => set('secret', e.target.value)}
                  maxLength={10_000}
                  aria-invalid={!!errors.secret}
                  aria-describedby={errors.secret ? 'secret-error' : undefined}
                  disabled={submitting}
                />
              )}
            </FormField>

            <FormField id="url" label="URL (opcional)" error={errors.url}>
              <input
                id="url"
                type="url"
                className={`input ${errors.url ? 'input-error' : ''}`}
                value={values.url}
                onChange={(e) => set('url', e.target.value)}
                maxLength={2048}
                placeholder="https://"
                aria-invalid={!!errors.url}
                aria-describedby={errors.url ? 'url-error' : undefined}
                disabled={submitting}
              />
            </FormField>

            <FormField id="description" label="Descrição (opcional)" error={errors.description}>
              <textarea
                id="description"
                rows={3}
                className={`input ${errors.description ? 'input-error' : ''}`}
                value={values.description}
                onChange={(e) => set('description', e.target.value)}
                maxLength={1000}
                aria-invalid={!!errors.description}
                aria-describedby={errors.description ? 'description-error' : undefined}
                disabled={submitting}
              />
            </FormField>

            <div className="flex justify-end gap-3 pt-2">
              <button type="button" className="btn btn-secondary" onClick={() => navigate(id ? `/items/${id}` : '/')} disabled={submitting}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Salvar
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
