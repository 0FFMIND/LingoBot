import { useState, useEffect, useCallback } from 'react';
import { authUtils, authService } from '../services';
import { UserDTO } from '../types';

export interface UseAuthResult {
  isAuthenticated: boolean;
  currentUser: UserDTO | null;
  initializing: boolean;
  login: typeof authService.login;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

export function useAuth(): UseAuthResult {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserDTO | null>(null);
  const [initializing, setInitializing] = useState(true);

  const handleGoogleOAuthCallback = useCallback(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const userId = urlParams.get('userId');
    const username = urlParams.get('username');
    const email = urlParams.get('email');
    const avatar = urlParams.get('avatar');
    
    if (token && userId && username) {
      const user: UserDTO = {
        id: parseInt(userId),
        username,
        email: email || undefined,
        avatar: avatar || undefined,
        createdAt: new Date().toISOString(),
      };
      
      authUtils.setAuth(token, user);
      
      urlParams.delete('token');
      urlParams.delete('userId');
      urlParams.delete('username');
      urlParams.delete('email');
      urlParams.delete('avatar');
      
      window.history.replaceState({}, '', window.location.pathname + (urlParams.toString() ? '?' + urlParams.toString() : ''));
      
      return { token, user };
    }
    return null;
  }, []);

  const refreshUser = useCallback(async () => {
    const token = authUtils.getToken();
    if (token) {
      try {
        const fetchedUser = await authService.getCurrentUser();
        authUtils.setAuth(token, fetchedUser);
        setIsAuthenticated(true);
        setCurrentUser(fetchedUser);
      } catch (error) {
        const user = authUtils.getUser();
        if (user) {
          setIsAuthenticated(true);
          setCurrentUser(user);
        } else {
          authUtils.clearAuth();
          setIsAuthenticated(false);
          setCurrentUser(null);
        }
      }
    }
  }, []);

  const logout = useCallback(() => {
    authService.logout();
    setIsAuthenticated(false);
    setCurrentUser(null);
  }, []);

  useEffect(() => {
    const oauthResult = handleGoogleOAuthCallback();
    
    if (oauthResult) {
      setIsAuthenticated(true);
      setCurrentUser(oauthResult.user);
      setInitializing(false);
      return;
    }
    
    const { token } = authUtils.initializeAuth();
    if (token) {
      refreshUser().finally(() => setInitializing(false));
    } else {
      setIsAuthenticated(false);
      setCurrentUser(null);
      setInitializing(false);
    }
  }, [handleGoogleOAuthCallback, refreshUser]);

  useEffect(() => {
    const handleLogin = (e: Event) => {
      const customEvent = e as CustomEvent<{ user: UserDTO }>;
      setIsAuthenticated(true);
      if (customEvent.detail?.user) {
        setCurrentUser(customEvent.detail.user);
      }
    };

    const handleLogoutEvent = () => {
      setIsAuthenticated(false);
      setCurrentUser(null);
    };

    window.addEventListener('auth:login', handleLogin);
    window.addEventListener('auth:logout', handleLogoutEvent);

    return () => {
      window.removeEventListener('auth:login', handleLogin);
      window.removeEventListener('auth:logout', handleLogoutEvent);
    };
  }, []);

  return {
    isAuthenticated,
    currentUser,
    initializing,
    login: authService.login,
    logout,
    refreshUser,
  };
}
