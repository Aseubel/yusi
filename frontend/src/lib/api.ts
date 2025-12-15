import axios from 'axios'
import { toast } from 'sonner'
import { API_BASE } from '../utils'
import { useAuthStore } from '../store/authStore'

export const api = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
})

api.interceptors.request.use((config) => {
  const { token, refreshToken } = useAuthStore.getState()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (refreshToken) {
    config.headers['X-Refresh-Token'] = refreshToken
  }
  return config
})

api.interceptors.response.use(
  (res) => {
    const newToken = res.headers['x-new-access-token']
    if (newToken) {
      useAuthStore.getState().setToken(newToken)
    }
    return res
  },
  (err) => {
    const msg = err.response?.data?.info || err.message
    if (err.response?.status === 401) {
       useAuthStore.getState().logout()
       // window.location.href = '/login' // Optional: redirect
    }
    toast.error(msg)
    return Promise.reject(err)
  }
)

export const authApi = {
  login: (data: any) => api.post('/user/login', data),
  register: (data: any) => api.post('/user/register', data),
}