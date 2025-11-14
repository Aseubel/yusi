import axios from 'axios'
import { toast } from 'sonner'
import { API_BASE } from '../utils'

export const api = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err.response?.data?.info || err.message
    toast.error(msg)
    return Promise.reject(err)
  }
)