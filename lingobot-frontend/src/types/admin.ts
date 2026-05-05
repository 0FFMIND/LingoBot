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
  isCurrentAdmin: boolean;
}
