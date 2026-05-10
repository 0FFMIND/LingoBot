import { httpClient, authUtils } from './httpClient';
import { 
  UserDTO, 
  AuthResponse, 
  LoginRequest, 
  RegisterRequest, 
  RegisterWithCodeRequest,
  SendLoginCodeRequest,
  LoginWithCodeRequest
} from '../types';

export const authService = {
  login: async (request: LoginRequest): Promise<AuthResponse> => {
    const response = await httpClient.postRaw('/auth/login', request);
    const result = await response.json();
    const data: AuthResponse = result.data;
    
    const user: UserDTO = {
      id: data.userId,
      username: data.username,
      role: data.role,
      avatar: data.avatar,
      createdAt: new Date().toISOString(),
      balance: data.balance ? Number(data.balance) : 0
    };
    
    authUtils.setAuth(data.token, user);
    window.dispatchEvent(new CustomEvent('auth:login', { detail: { user } }));
    
    return data;
  },
  
  sendLoginCode: async (request: SendLoginCodeRequest): Promise<void> => {
    await httpClient.post('/auth/send-login-code', request);
  },
  
  loginWithCode: async (request: LoginWithCodeRequest): Promise<AuthResponse> => {
    const response = await httpClient.postRaw('/auth/login-with-code', request);
    const result = await response.json();
    const data: AuthResponse = result.data;
    
    const user: UserDTO = {
      id: data.userId,
      username: data.username,
      email: data.email,
      role: data.role,
      avatar: data.avatar,
      createdAt: new Date().toISOString(),
      balance: data.balance ? Number(data.balance) : 0
    };
    
    authUtils.setAuth(data.token, user);
    window.dispatchEvent(new CustomEvent('auth:login', { detail: { user } }));
    
    return data;
  },
  
  register: async (request: RegisterRequest): Promise<AuthResponse> => {
    const response = await httpClient.postRaw('/auth/register', request);
    const result = await response.json();
    const data: AuthResponse = result.data;
    
    const user: UserDTO = {
      id: data.userId,
      username: data.username,
      email: data.email,
      role: data.role,
      avatar: data.avatar,
      createdAt: new Date().toISOString(),
      balance: data.balance ? Number(data.balance) : 0
    };
    
    authUtils.setAuth(data.token, user);
    window.dispatchEvent(new CustomEvent('auth:login', { detail: { user } }));
    
    return data;
  },
  
  sendVerificationCode: async (email: string): Promise<void> => {
    await httpClient.post('/auth/send-verification-code', { email });
  },
  
  registerWithCode: async (request: RegisterWithCodeRequest): Promise<AuthResponse> => {
    const response = await httpClient.postRaw('/auth/register-with-code', request);
    const result = await response.json();
    const data: AuthResponse = result.data;
    
    const user: UserDTO = {
      id: data.userId,
      username: data.username,
      email: request.email,
      role: data.role,
      avatar: data.avatar,
      createdAt: new Date().toISOString(),
      balance: data.balance ? Number(data.balance) : 0
    };
    
    authUtils.setAuth(data.token, user);
    window.dispatchEvent(new CustomEvent('auth:login', { detail: { user } }));
    
    return data;
  },
  
  getCurrentUser: async (): Promise<UserDTO> => {
    const user = await httpClient.get<UserDTO>('/auth/me');
    return user;
  },
  
  logout: () => {
    authUtils.clearAuth();
    window.dispatchEvent(new CustomEvent('auth:logout'));
  },
  
  changePassword: async (currentPassword: string, newPassword: string, confirmPassword: string): Promise<void> => {
    await httpClient.post('/auth/change-password', { 
      currentPassword, 
      newPassword, 
      confirmPassword 
    });
  },
  
  deactivateAccount: async (): Promise<void> => {
    await httpClient.post('/auth/deactivate');
    authUtils.clearAuth();
    window.dispatchEvent(new CustomEvent('auth:logout'));
  },
  
  updateAvatar: async (avatar: string): Promise<void> => {
    await httpClient.post('/auth/update-avatar', { avatar });
  },
  
  updateUsername: async (username: string): Promise<AuthResponse> => {
    const response = await httpClient.postRaw('/auth/update-username', { username });
    const result = await response.json();
    const data: AuthResponse = result.data;
    
    const updatedUser: UserDTO = {
      id: data.userId,
      username: data.username,
      email: data.email,
      role: data.role,
      avatar: data.avatar,
      createdAt: new Date().toISOString(),
      balance: data.balance ? Number(data.balance) : 0
    };
    
    authUtils.setAuth(data.token, updatedUser);
    window.dispatchEvent(new CustomEvent('auth:username-updated', { detail: { user: updatedUser } }));
    
    return data;
  }
};
