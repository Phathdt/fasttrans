import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'

// Single axios instance shared across all generated API calls.
// The SPA is a separate origin (localhost:3000) and calls Traefik cross-origin.
// baseURL comes from VITE_API_BASE_URL, defaulting to the Traefik host port.
export const AXIOS_INSTANCE = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4000',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach Bearer token from localStorage for every request except /auth/login
AXIOS_INSTANCE.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  const url = config.url ?? ''
  if (token && !url.includes('/auth/login')) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// On 401: clear session and redirect to /login
AXIOS_INSTANCE.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

// customInstance is the mutator used by orval-generated code.
// All service responses are wrapped as { data: T, meta: Meta } by the backend
// ResponseBodyAdvice. We unwrap one layer here so callers receive T directly.
//
// Unwrap<T>: if orval passes a generated envelope type (e.g. Login200 = { data: LoginResponse, meta: Meta })
// the conditional type resolves the inner payload so hooks return the raw DTO.
// If T has no `data` key (legacy / direct call) it passes through unchanged.
type Unwrap<T> = T extends { data: infer D } ? D : T

export const customInstance = <T>(
  config: AxiosRequestConfig,
): Promise<Unwrap<T>> => {
  return AXIOS_INSTANCE(config).then((response) => {
    const envelope = response.data as { data: unknown }
    if (envelope !== null && typeof envelope === 'object' && 'data' in envelope) {
      return envelope.data as Unwrap<T>
    }
    return response.data as Unwrap<T>
  })
}

// Shape of the backend error envelope's error body.
export interface ApiErrorBody {
  code: string
  message: string
  details?: Array<{ field: string; message: string }>
}

/**
 * Extract structured error info from an Axios error response.
 * Reads `err.response.data.error` (ErrorBody) and `err.response.data.meta.requestId`.
 * Falls back to HTTP status text when the body is absent (e.g. network errors).
 */
export function extractApiError(err: unknown): {
  code: string | null
  message: string
  details: Array<{ field: string; message: string }>
  requestId: string | null
} {
  if (!axios.isAxiosError(err)) {
    return { code: null, message: 'An unexpected error occurred.', details: [], requestId: null }
  }

  const axiosErr = err as AxiosError<{ error?: ApiErrorBody; meta?: { requestId?: string } }>
  const errorBody = axiosErr.response?.data?.error
  const requestId = axiosErr.response?.data?.meta?.requestId ?? null

  if (errorBody) {
    return {
      code: errorBody.code,
      message: errorBody.message,
      details: errorBody.details ?? [],
      requestId,
    }
  }

  // Fallback: no structured body (network error, proxy error, etc.)
  const statusText = axiosErr.response?.statusText ?? 'Request failed'
  return { code: null, message: statusText, details: [], requestId }
}

