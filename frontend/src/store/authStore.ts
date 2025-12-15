import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface User {
  id: number
  userId: string
  userName: string
  email?: string
}

interface AuthState {
  user: User | null
  token: string | null
  refreshToken: string | null
  login: (user: User, token: string, refreshToken: string) => void
  logout: () => void
  setToken: (token: string) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      refreshToken: null,
      login: (user, token, refreshToken) => set({ user, token, refreshToken }),
      logout: () => set({ user: null, token: null, refreshToken: null }),
      setToken: (token) => set({ token }),
    }),
    {
      name: 'yusi-auth-storage',
    }
  )
)
