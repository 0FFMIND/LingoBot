import { create } from 'zustand'
import { UserDTO } from '../types'
import { authUtils, authApi } from '../api'

interface InsufficientBalanceData {
  message: string
  currentBalance?: number
  requiredCost?: number
}

interface AuthStore {
  isAuthenticated: boolean
  currentUser: UserDTO | null
  initializing: boolean
  showAuthModal: boolean
  showDeactivateModal: boolean
  showInsufficientBalanceModal: boolean
  insufficientBalanceData: InsufficientBalanceData
  initAuth: () => Promise<void>
  setCurrentUser: (user: UserDTO | null) => void
  setShowAuthModal: (v: boolean) => void
  setShowDeactivateModal: (v: boolean) => void
  showInsufficientBalance: (data: InsufficientBalanceData) => void
  closeInsufficientBalance: () => void
  logout: () => void
}

export const useAuthStore = create<AuthStore>((set) => ({
  isAuthenticated: false,
  currentUser: null,
  initializing: true,
  showAuthModal: false,
  showDeactivateModal: false,
  showInsufficientBalanceModal: false,
  insufficientBalanceData: { message: '你的余额不足' },

  initAuth: async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const token = urlParams.get('token')
    const userId = urlParams.get('userId')
    const username = urlParams.get('username')

    if (token && userId && username) {
      const user: UserDTO = {
        id: parseInt(userId),
        username,
        email: urlParams.get('email') || undefined,
        avatar: urlParams.get('avatar') || undefined,
        createdAt: new Date().toISOString(),
      }
      authUtils.setAuth(token, user)
      ;['token', 'userId', 'username', 'email', 'avatar'].forEach(k => urlParams.delete(k))
      window.history.replaceState({}, '', window.location.pathname + (urlParams.toString() ? '?' + urlParams.toString() : ''))
      set({ isAuthenticated: true, currentUser: user, initializing: false })
      return
    }

    const existingToken = authUtils.initializeAuth()
    if (existingToken) {
      try {
        const fetchedUser = await authApi.getCurrentUser()
        authUtils.setUser(fetchedUser)
        set({ isAuthenticated: true, currentUser: fetchedUser, initializing: false })
      } catch {
        authUtils.clearAuth()
        set({ isAuthenticated: false, currentUser: null, initializing: false })
      }
    } else {
      set({ isAuthenticated: false, currentUser: null, initializing: false })
    }
  },

  setCurrentUser: (user) => set({ currentUser: user }),
  setShowAuthModal: (v) => set({ showAuthModal: v }),
  setShowDeactivateModal: (v) => set({ showDeactivateModal: v }),
  showInsufficientBalance: (data) => set({ showInsufficientBalanceModal: true, insufficientBalanceData: data }),
  closeInsufficientBalance: () => set({ showInsufficientBalanceModal: false }),
  logout: () => set({ isAuthenticated: false, currentUser: null }),
}))
