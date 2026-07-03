import axios, { type AxiosRequestConfig } from 'axios'

// Single axios instance shared across all generated API calls.
// baseURL is '' so that paths like /api/auth/login are used as-is,
// matching the Vite dev proxy and production nginx setup.
export const AXIOS_INSTANCE = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach Bearer token from localStorage for every request except /api/auth/login
AXIOS_INSTANCE.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  const url = config.url ?? ''
  if (token && !url.includes('/api/auth/login')) {
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
