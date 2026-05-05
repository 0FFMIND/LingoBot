import { httpClient } from './httpClient';
import { 
  RedemptionCodeDTO, 
  CreateRedemptionCodeRequest, 
  RedeemCodeRequest 
} from '../types';

export const redemptionService = {
  getBalance: async (): Promise<number> => {
    return httpClient.get<number>('/redemption/balance');
  },

  redeemCode: async (code: string): Promise<RedemptionCodeDTO> => {
    return httpClient.post<RedemptionCodeDTO>('/redemption/redeem', { code } as RedeemCodeRequest);
  },

  createCode: async (points: number): Promise<RedemptionCodeDTO> => {
    return httpClient.post<RedemptionCodeDTO>('/redemption/codes', { points } as CreateRedemptionCodeRequest);
  },

  getAllCodes: async (): Promise<RedemptionCodeDTO[]> => {
    return httpClient.get<RedemptionCodeDTO[]>('/redemption/codes');
  },

  getCodeById: async (id: number): Promise<RedemptionCodeDTO> => {
    return httpClient.get<RedemptionCodeDTO>(`/redemption/codes/${id}`);
  },

  deleteCode: async (id: number): Promise<void> => {
    return httpClient.delete<void>(`/redemption/codes/${id}`);
  },
};
