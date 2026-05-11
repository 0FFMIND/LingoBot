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
    const tryDevAutoLogin = async (): Promise<boolean> => {
      const devAuthResult = await authApi.devAutoLogin()
      if (!devAuthResult) {
        return false
      }

      const user: UserDTO = {
        id: devAuthResult.userId,
        username: devAuthResult.username,
        email: devAuthResult.email,
        role: devAuthResult.role,
        avatar: devAuthResult.avatar,
        createdAt: new Date().toISOString(),
        balance: devAuthResult.balance ? Number(devAuthResult.balance) : 0
      }
      authUtils.setUser(user)
      set({ isAuthenticated: true, currentUser: user, initializing: false, showAuthModal: false })
      return true
    }

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
      set({ isAuthenticated: true, currentUser: user, initializing: false, showAuthModal: false })
      return
    }

    const existingToken = authUtils.initializeAuth()
    if (existingToken) {
      try {
        const fetchedUser = await authApi.getCurrentUser()
        authUtils.setUser(fetchedUser)
        set({ isAuthenticated: true, currentUser: fetchedUser, initializing: false, showAuthModal: false })
      } catch {
        authUtils.clearAuth()
        const devLoggedIn = await tryDevAutoLogin()
        if (!devLoggedIn) {
          set({ isAuthenticated: false, currentUser: null, initializing: false })
        }
      }
      return
    }

    const devLoggedIn = await tryDevAutoLogin()
    if (devLoggedIn) {
      return
    }

    set({ isAuthenticated: false, currentUser: null, initializing: false })
  },

  setCurrentUser: (user) => set({ currentUser: user }),
  setShowAuthModal: (v) => set({ showAuthModal: v }),
  setShowDeactivateModal: (v) => set({ showDeactivateModal: v }),
  showInsufficientBalance: (data) => set({ showInsufficientBalanceModal: true, insufficientBalanceData: data }),
  closeInsufficientBalance: () => set({ showInsufficientBalanceModal: false }),
  logout: () => set({ isAuthenticated: false, currentUser: null }),
}))
