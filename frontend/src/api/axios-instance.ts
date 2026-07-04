import axios, { type AxiosRequestConfig } from 'axios'

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
// It unwraps response.data so callers receive T directly.
export const customInstance = <T>(
  config: AxiosRequestConfig,
): Promise<T> => {
  return AXIOS_INSTANCE(config).then((response) => response.data as T)
}
