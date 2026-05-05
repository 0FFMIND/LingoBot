import { UserDTO } from '../types';

const API_BASE = '/api';
const TOKEN_KEY = 'lingobot_token';
const USER_KEY = 'lingobot_user';

let currentToken: string | null = localStorage.getItem(TOKEN_KEY);
let currentUser: UserDTO | null = null;

export const isJwtExpired = (token: string): boolean => {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return true;
    }
    
    const payload = parts[1];
    const decodedPayload = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const payloadObj = JSON.parse(decodedPayload);
    
    if (!payloadObj.exp) {
      return true;
    }
    
    const expirationTime = payloadObj.exp * 1000;
    const currentTime = Date.now();
    
    return currentTime >= expirationTime;
  } catch (error) {
    console.error('Failed to parse JWT:', error);
    return true;
  }
};

export const authUtils = {
  getToken: (): string | null => currentToken,
  
  getUser: (): UserDTO | null => currentUser,
  
  setAuth: (token: string, user: UserDTO) => {
    currentToken = token;
    currentUser = user;
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  
  clearAuth: () => {
    currentToken = null;
    currentUser = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
  
  isAuthenticated: (): boolean => {
    if (currentToken === null) {
      return false;
    }
    return !isJwtExpired(currentToken);
  },
  
  initializeAuth: (): { token: string | null; user: UserDTO | null } => {
    const token = localStorage.getItem(TOKEN_KEY);
    const userStr = localStorage.getItem(USER_KEY);
    
    if (token && isJwtExpired(token)) {
      currentToken = null;
      currentUser = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      return { token: null, user: null };
    }
    
    currentToken = token;
    currentUser = userStr ? JSON.parse(userStr) : null;
    
    return { token: currentToken, user: currentUser };
  }
};

export const getAuthHeaders = (): HeadersInit => {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  
  if (currentToken) {
    headers['Authorization'] = `Bearer ${currentToken}`;
  }
  
  return headers;
};

export const handleResponse = async (response: Response) => {
  if (response.status === 401) {
    authUtils.clearAuth();
    window.dispatchEvent(new CustomEvent('auth:logout'));
    throw new Error('请先登录');
  }
  
  if (!response.ok) {
    let errorMessage = '请求失败';
    try {
      const errorData = await response.json();
      errorMessage = errorData.message || errorMessage;
    } catch {
      // 忽略解析错误
    }
    throw new Error(errorMessage);
  }
  
  return response;
};

export const httpClient = {
  get: async <T>(url: string): Promise<T> => {
    const response = await fetch(`${API_BASE}${url}`, {
      headers: getAuthHeaders(),
    });
    await handleResponse(response);
    const result = await response.json();
    return result.data;
  },
  
  post: async <T>(url: string, body?: unknown): Promise<T> => {
    const response = await fetch(`${API_BASE}${url}`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    await handleResponse(response);
    const result = await response.json();
    return result.data;
  },
  
  put: async <T>(url: string, body?: unknown): Promise<T> => {
    const response = await fetch(`${API_BASE}${url}`, {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    await handleResponse(response);
    const result = await response.json();
    return result.data;
  },
  
  delete: async <T>(url: string): Promise<T> => {
    const response = await fetch(`${API_BASE}${url}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    await handleResponse(response);
    const result = await response.json();
    return result.data;
  },
  
  getRaw: async (url: string): Promise<Response> => {
    const response = await fetch(`${API_BASE}${url}`, {
      headers: getAuthHeaders(),
    });
    await handleResponse(response);
    return response;
  },
  
  postRaw: async (url: string, body?: unknown): Promise<Response> => {
    const response = await fetch(`${API_BASE}${url}`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    await handleResponse(response);
    return response;
  },
  
  putRaw: async (url: string, body?: unknown): Promise<Response> => {
    const response = await fetch(`${API_BASE}${url}`, {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: body ? JSON.stringify(body) : undefined,
    });
    await handleResponse(response);
    return response;
  },
};

export { API_BASE };
