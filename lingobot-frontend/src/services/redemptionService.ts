import { httpClient } from './httpClient';
import { 
  RedemptionCodeDTO, 
  RedemptionCodeUsageDTO,
  CreateRedemptionCodeRequest, 
  RedeemCodeRequest 
} from '../types';

export const redemptionService = {
  redeemCode: async (code: string): Promise<RedemptionCodeDTO> => {
    return httpClient.post<RedemptionCodeDTO>('/redemption/redeem', { code } as RedeemCodeRequest);
  },

  createCode: async (points: number, expiresInSeconds?: number, maxUsages?: number): Promise<RedemptionCodeDTO> => {
    const request: CreateRedemptionCodeRequest = { points };
    if (expiresInSeconds !== undefined && expiresInSeconds > 0) {
      request.expiresInSeconds = expiresInSeconds;
    }
    if (maxUsages !== undefined && maxUsages > 0) {
      request.maxUsages = maxUsages;
    }
    return httpClient.post<RedemptionCodeDTO>('/redemption/codes', request);
  },

  getAllCodes: async (): Promise<RedemptionCodeDTO[]> => {
    return httpClient.get<RedemptionCodeDTO[]>('/redemption/codes');
  },

  getCodeById: async (id: number): Promise<RedemptionCodeDTO> => {
    return httpClient.get<RedemptionCodeDTO>(`/redemption/codes/${id}`);
  },

  getCodeUsages: async (id: number): Promise<RedemptionCodeUsageDTO[]> => {
    return httpClient.get<RedemptionCodeUsageDTO[]>(`/redemption/codes/${id}/usages`);
  },

  deleteCode: async (id: number): Promise<void> => {
    return httpClient.delete<void>(`/redemption/codes/${id}`);
  },
};
