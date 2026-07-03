// API types

export interface LoginResponse {
  token: string
  expiresIn: number
}

export interface Account {
  accountRef: string
  ownerName: string
  balance: number
  currency: string
}

export interface Transfer {
  id: string
  fromAccountRef: string
  toAccountRef: string
  amount: number
  currency: string
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  reason: string | null
  createdAt: string
}

export interface CreateTransferBody {
  fromAccountRef: string
  toAccountRef: string
  amount: number
  currency: string
}

export interface CreateTransferResponse {
  id: string
  status: string
}

const BASE = '/api'

function getToken(): string | null {
  return localStorage.getItem('token')
}

function clearSession(): void {
  localStorage.removeItem('token')
  window.location.href = '/login'
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  skipAuth = false,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  }

  if (!skipAuth) {
    const token = getToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  const res = await fetch(`${BASE}${path}`, { ...options, headers })

  if (res.status === 401) {
    clearSession()
    // clearSession() redirects; throw so callers don't proceed
    throw new Error('Unauthorized')
  }

  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`) as Error & { status: number }
    err.status = res.status
    throw err
  }

  // 204 / 201 with no body
  const text = await res.text()
  if (!text) return undefined as unknown as T
  return JSON.parse(text) as T
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>(
    '/auth/login',
    {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    },
    true, // no auth header for login itself
  )
}

export async function getAccounts(): Promise<Account[]> {
  return request<Account[]>('/accounts')
}

export async function createTransfer(
  body: CreateTransferBody,
  idempotencyKey: string,
): Promise<CreateTransferResponse> {
  return request<CreateTransferResponse>('/transfers', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(body),
  })
}

export async function listTransfers(): Promise<Transfer[]> {
  return request<Transfer[]>('/transfers')
}

export async function getTransfer(id: string): Promise<Transfer> {
  return request<Transfer>(`/transfers/${id}`)
}
