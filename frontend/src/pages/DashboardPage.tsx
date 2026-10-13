import { Eye, KeyRound, Loader2, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiRequestError } from '../api/client'
import { ITEM_TYPES, typeLabel, type VaultItemSummary, type VaultItemType } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../lib/format'

export function DashboardPage() {
  const toast = useToast()
  const [items, setItems] = useState<VaultItemSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState<VaultItemType | 'ALL'>('ALL')
  const [toDelete, setToDelete] = useState<VaultItemSummary | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    setItems(null)
    try {
      setItems(await api.listItems())
    } catch (e) {
      // 401 já é tratado globalmente (redireciona ao login).
      if (!(e instanceof ApiRequestError && e.status === 401)) {
        setError(e instanceof ApiRequestError ? e.message : 'Não foi possível carregar os registros.')
      }
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase()
    return (items ?? []).filter(
      (item) =>
        (typeFilter === 'ALL' || item.type === typeFilter) &&
        (!term || item.title.toLowerCase().includes(term) || (item.description ?? '').toLowerCase().includes(term)),
    )
  }, [items, query, typeFilter])

  async function confirmDelete() {
    if (!toDelete) return
    setDeleting(true)
    try {
      await api.deleteItem(toDelete.id)
      setItems((current) => (current ?? []).filter((i) => i.id !== toDelete.id))
      toast.success('Registro excluído.')
      setToDelete(null)
    } catch (e) {
      toast.error(e instanceof ApiRequestError ? e.message : 'Não foi possível excluir o registro.')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Meus registros</h1>
          <p className="text-sm text-slate-400">Somente você tem acesso a estes dados.</p>
        </div>
        <Link to="/items/new" className="btn btn-primary">
          <Plus className="h-4 w-4" aria-hidden />
          Novo registro
        </Link>
      </div>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-500" aria-hidden />
          <input
            type="search"
            className="input pl-9"
            placeholder="Buscar por título ou descrição"
            aria-label="Buscar registros"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <select
          className="input sm:w-48"
          aria-label="Filtrar por tipo"
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as VaultItemType | 'ALL')}
        >
          <option value="ALL">Todos os tipos</option>
          {ITEM_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-6">
        {items === null && !error && (
          <div className="flex items-center justify-center py-16 text-slate-400" role="status">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" aria-hidden />
            Carregando registros...
          </div>
        )}

        {error && (
          <div className="card p-6 text-center" role="alert">
            <p className="text-red-300">{error}</p>
            <button type="button" className="btn btn-secondary mt-4" onClick={() => void load()}>
              Tentar novamente
            </button>
          </div>
        )}

        {items !== null && items.length === 0 && (
          <div className="card flex flex-col items-center px-6 py-16 text-center">
            <KeyRound className="h-10 w-10 text-slate-600" aria-hidden />
            <h2 className="mt-4 text-lg font-medium">Seu cofre está vazio</h2>
            <p className="mt-1 text-sm text-slate-400">Adicione sua primeira credencial, chave de API ou nota segura.</p>
            <Link to="/items/new" className="btn btn-primary mt-6">
              <Plus className="h-4 w-4" aria-hidden />
              Novo registro
            </Link>
          </div>
        )}

        {items !== null && items.length > 0 && filtered.length === 0 && (
          <p className="py-12 text-center text-sm text-slate-400">Nenhum registro corresponde à busca.</p>
        )}

        {filtered.length > 0 && (
          <ul className="space-y-3">
            {filtered.map((item) => (
              <li key={item.id} className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <Link to={`/items/${item.id}`} className="truncate font-medium hover:text-emerald-400">
                      {item.title}
                    </Link>
                    <span className="shrink-0 rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-300">
                      {typeLabel(item.type)}
                    </span>
                  </div>
                  <p className="mt-1 truncate text-xs text-slate-500">Atualizado em {formatDateTime(item.updatedAt)}</p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Link to={`/items/${item.id}`} className="btn btn-secondary px-3" aria-label={`Visualizar ${item.title}`}>
                    <Eye className="h-4 w-4" aria-hidden />
                  </Link>
                  <Link to={`/items/${item.id}/edit`} className="btn btn-secondary px-3" aria-label={`Editar ${item.title}`}>
                    <Pencil className="h-4 w-4" aria-hidden />
                  </Link>
                  <button
                    type="button"
                    className="btn btn-secondary px-3 text-red-400"
                    aria-label={`Excluir ${item.title}`}
                    onClick={() => setToDelete(item)}
                  >
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {toDelete && (
        <ConfirmDialog
          title="Excluir registro"
          message={`Tem certeza de que deseja excluir "${toDelete.title}"? Esta ação não pode ser desfeita.`}
          confirmLabel="Excluir"
          busy={deleting}
          onConfirm={() => void confirmDelete()}
          onCancel={() => setToDelete(null)}
        />
      )}
    </div>
  )
}
