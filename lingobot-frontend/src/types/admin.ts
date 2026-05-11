export interface BlockedUserInfo {
  userId: number;
  username: string;
  blockedAt: string;
  expiresAt: string;
  remainingSeconds: number;
}

export interface BlockedIpInfo {
  ipAddress: string;
  blockedAt: string;
  expiresAt: string;
  remainingSeconds: number;
}

export interface LockedEmailInfo {
  email: string;
  lockedAt: string;
  expiresAt: string;
  remainingSeconds: number;
  lockType: string;
}

export interface LockedEmailIpInfo {
  ipAddress: string;
  lockedAt: string;
  expiresAt: string;
  remainingSeconds: number;
  lockType: string;
}

export interface AdminStatus {
  blockedUserCount: number;
  blockedIpCount: number;
}

export interface UserAdminDTO {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
  balance: number;
  isCurrentAdmin: boolean;
}

export interface UpdateBalanceRequest {
  newBalance: number;
}
