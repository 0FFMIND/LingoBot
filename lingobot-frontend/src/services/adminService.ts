import { httpClient } from './httpClient';
import { 
  BlockedUserInfo, 
  BlockedIpInfo, 
  AdminStatus, 
  UserAdminDTO,
  UpdateBalanceRequest
} from '../types';

export const adminService = {
  getBlockedUsers: async (): Promise<BlockedUserInfo[]> => {
    return httpClient.get<BlockedUserInfo[]>('/admin/blocked-users');
  },

  getBlockedIps: async (): Promise<BlockedIpInfo[]> => {
    return httpClient.get<BlockedIpInfo[]>('/admin/blocked-ips');
  },

  unlockUser: async (userId: number): Promise<void> => {
    return httpClient.post<void>(`/admin/unlock-user/${userId}`);
  },

  unblockIp: async (ipAddress: string): Promise<void> => {
    return httpClient.post<void>(`/admin/unblock-ip/${encodeURIComponent(ipAddress)}`);
  },

  getStatus: async (): Promise<AdminStatus> => {
    return httpClient.get<AdminStatus>('/admin/status');
  },

  getAllUsers: async (): Promise<UserAdminDTO[]> => {
    return httpClient.get<UserAdminDTO[]>('/admin/users');
  },

  deleteUser: async (userId: number): Promise<void> => {
    return httpClient.delete<void>(`/admin/users/${userId}`);
  },

  resetUserPassword: async (userId: number, newPassword: string): Promise<void> => {
    return httpClient.post<void>(`/admin/users/${userId}/reset-password`, { newPassword });
  },

  updateUserUsername: async (userId: number, newUsername: string): Promise<void> => {
    return httpClient.put<void>(`/admin/users/${userId}/username`, { newUsername });
  },

  updateUserBalance: async (userId: number, newBalance: number): Promise<void> => {
    const request: UpdateBalanceRequest = { newBalance };
    return httpClient.put<void>(`/admin/users/${userId}/balance`, request);
  },
};
