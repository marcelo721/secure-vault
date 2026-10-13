import type {
  ApiErrorBody,
  FieldViolation,
  User,
  VaultItemDetail,
  VaultItemInput,
  VaultItemSummary,
} from './types'

const BASE = '/api/v1'

export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldViolation[]

  constructor(status: number, code: string, message: string, fieldErrors: FieldViolation[] = []) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

/** Registrado pelo AuthProvider: chamado quando uma rota privada responde 401 (sessão expirada/inválida). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler
}

function readCsrfCookie(): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : null
}

async function ensureCsrfToken(): Promise<string | null> {
  let token = readCsrfCookie()
  if (!token) {
    // O backend emite o cookie XSRF-TOKEN em qualquer resposta; este endpoint só serve para isso.
    await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
    token = readCsrfCookie()
  }
  return token
}

interface RequestOptions {
  body?: unknown
  /** Rotas públicas (login/cadastro) não disparam o tratamento global de sessão expirada. */
  publicRoute?: boolean
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const token = await ensureCsrfToken()
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiRequestError(0, 'NETWORK_ERROR', 'Não foi possível conectar ao servidor. Verifique sua conexão.')
  }

  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T
  }

  let body: Partial<ApiErrorBody> = {}
  try {
    body = (await response.json()) as Partial<ApiErrorBody>
  } catch {
    // Resposta sem corpo JSON: usa mensagem genérica abaixo.
  }

  if (response.status === 401 && !options.publicRoute) {
    onUnauthorized?.()
  }

  throw new ApiRequestError(
    response.status,
    body.code ?? 'ERROR',
    body.message ?? 'Não foi possível concluir a operação.',
    body.fieldErrors ?? [],
  )
}

function normalizeItem(input: VaultItemInput) {
  // Campos opcionais vazios viram null; o backend aceita string vazia na URL, mas null é mais consistente.
  return {
    title: input.title.trim(),
    type: input.type,
    username: input.username.trim() || null,
    secret: input.secret,
    url: input.url.trim() || null,
    description: input.description.trim() || null,
  }
}

export const api = {
  register: (name: string, email: string, password: string) =>
    request<User>('POST', '/auth/register', { body: { name, email, password }, publicRoute: true }),
  login: (email: string, password: string) =>
    request<User>('POST', '/auth/login', { body: { email, password }, publicRoute: true }),
  logout: () => request<void>('POST', '/auth/logout', { publicRoute: true }),
  me: () => request<User>('GET', '/auth/me', { publicRoute: true }),

  listItems: () => request<VaultItemSummary[]>('GET', '/vault-items'),
  getItem: (id: string) => request<VaultItemDetail>('GET', `/vault-items/${encodeURIComponent(id)}`),
  createItem: (input: VaultItemInput) =>
    request<VaultItemSummary>('POST', '/vault-items', { body: normalizeItem(input) }),
  updateItem: (id: string, input: VaultItemInput) =>
    request<VaultItemSummary>('PUT', `/vault-items/${encodeURIComponent(id)}`, { body: normalizeItem(input) }),
  deleteItem: (id: string) => request<void>('DELETE', `/vault-items/${encodeURIComponent(id)}`),
}
