import { ArrowLeft, ExternalLink, Loader2, Pencil, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiRequestError } from '../api/client'
import { typeLabel, type VaultItemDetail } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { SecretField } from '../components/SecretField'
import { useToast } from '../components/Toast'
import { formatDateTime, safeHttpUrl } from '../lib/format'

export function ItemDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [item, setItem] = useState<VaultItemDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let active = true
    setItem(null)
    setError(null)
    api
      .getItem(id)
      .then((data) => {
        if (active) setItem(data)
      })
      .catch((e: unknown) => {
        if (!active || (e instanceof ApiRequestError && e.status === 401)) return
        setError(
          e instanceof ApiRequestError && e.status === 404
            ? 'Registro não encontrado.'
            : e instanceof ApiRequestError
              ? e.message
              : 'Não foi possível carregar o registro.',
        )
      })
    return () => {
      active = false
      // O segredo descriptografado existe apenas no estado deste componente e é descartado ao sair.
    }
  }, [id])

  async function confirmDelete() {
    setDeleting(true)
    try {
      await api.deleteItem(id)
      toast.success('Registro excluído.')
      navigate('/', { replace: true })
    } catch (e) {
      toast.error(e instanceof ApiRequestError ? e.message : 'Não foi possível excluir o registro.')
      setDeleting(false)
      setConfirming(false)
    }
  }

  const link = item ? safeHttpUrl(item.url) : null

  return (
    <div className="mx-auto max-w-2xl">
      <Link to="/" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-400 hover:text-slate-200">
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Voltar
      </Link>

      {!item && !error && (
        <div className="flex items-center justify-center py-16 text-slate-400" role="status">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" aria-hidden />
          Carregando...
        </div>
      )}

      {error && (
        <div className="card p-6 text-center" role="alert">
          <p className="text-red-300">{error}</p>
        </div>
      )}

      {item && (
        <div className="card p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <h1 className="break-words text-2xl font-semibold">{item.title}</h1>
              <span className="mt-2 inline-block rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-300">
                {typeLabel(item.type)}
              </span>
            </div>
            <div className="flex gap-2">
              <Link to={`/items/${item.id}/edit`} className="btn btn-secondary">
                <Pencil className="h-4 w-4" aria-hidden />
                Editar
              </Link>
              <button type="button" className="btn btn-secondary text-red-400" onClick={() => setConfirming(true)}>
                <Trash2 className="h-4 w-4" aria-hidden />
                Excluir
              </button>
            </div>
          </div>

          <dl className="mt-6 space-y-5 text-sm">
            {item.username && (
              <div>
                <dt className="mb-1 text-slate-400">Usuário / identificador</dt>
                <dd className="break-all">{item.username}</dd>
              </div>
            )}
            <div>
              <dt className="mb-1 text-slate-400">{item.type === 'SECURE_NOTE' ? 'Conteúdo' : 'Segredo'}</dt>
              <dd>
                <SecretField secret={item.secret} multiline={item.type === 'SECURE_NOTE'} />
              </dd>
            </div>
            {item.url && (
              <div>
                <dt className="mb-1 text-slate-400">URL</dt>
                <dd className="break-all">
                  {link ? (
                    <a
                      href={link}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-emerald-400 hover:underline"
                    >
                      {item.url}
                      <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                    </a>
                  ) : (
                    item.url
                  )}
                </dd>
              </div>
            )}
            {item.description && (
              <div>
                <dt className="mb-1 text-slate-400">Descrição</dt>
                <dd className="whitespace-pre-wrap break-words">{item.description}</dd>
              </div>
            )}
          </dl>

          <p className="mt-6 border-t border-slate-800 pt-4 text-xs text-slate-500">
            Criado em {formatDateTime(item.createdAt)} · Atualizado em {formatDateTime(item.updatedAt)}
          </p>
        </div>
      )}

      {confirming && item && (
        <ConfirmDialog
          title="Excluir registro"
          message={`Tem certeza de que deseja excluir "${item.title}"? Esta ação não pode ser desfeita.`}
          confirmLabel="Excluir"
          busy={deleting}
          onConfirm={() => void confirmDelete()}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  )
}
