export type VaultItemType = 'CREDENTIAL' | 'API_KEY' | 'SECURE_NOTE' | 'OTHER'

export const ITEM_TYPES: { value: VaultItemType; label: string }[] = [
  { value: 'CREDENTIAL', label: 'Credencial' },
  { value: 'API_KEY', label: 'Chave de API' },
  { value: 'SECURE_NOTE', label: 'Nota segura' },
  { value: 'OTHER', label: 'Outro' },
]

export function typeLabel(type: VaultItemType): string {
  return ITEM_TYPES.find((t) => t.value === type)?.label ?? type
}

export interface User {
  id: string
  name: string
  email: string
}

/** Listagem/criação/atualização: nunca contém conteúdo confidencial. */
export interface VaultItemSummary {
  id: string
  title: string
  type: VaultItemType
  url: string | null
  description: string | null
  createdAt: string
  updatedAt: string
}

/** Detalhe: única resposta que traz o segredo descriptografado. */
export interface VaultItemDetail extends VaultItemSummary {
  username: string | null
  secret: string
}

export interface VaultItemInput {
  title: string
  type: VaultItemType
  username: string
  secret: string
  url: string
  description: string
}

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiErrorBody {
  status: number
  code: string
  message: string
  fieldErrors?: FieldViolation[]
}
