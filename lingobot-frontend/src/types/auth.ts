export interface UserDTO {
  id: number;
  username: string;
  email?: string;
  role?: string;
  avatar?: string;
  createdAt: string;
  balance?: number;
}

export interface AuthResponse {
  token: string;
  username: string;
  userId: number;
  role?: string;
  email?: string;
  avatar?: string;
  balance?: number;
}

export interface LoginRequest {
  email?: string;
  username?: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email?: string;
  password: string;
}

export interface RegisterWithCodeRequest {
  email: string;
  password: string;
  verificationCode: string;
}
