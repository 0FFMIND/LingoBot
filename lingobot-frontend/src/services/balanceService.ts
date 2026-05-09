import { httpClient } from './httpClient';
import { BalanceTransactionDTO, BalanceTransactionPageResponse } from '../types';

export const balanceService = {
  getBalance: async (): Promise<number> => {
    return httpClient.get<number>('/redemption/balance');
  },

  getTransactions: async (page: number = 0, size: number = 20): Promise<BalanceTransactionPageResponse> => {
    return httpClient.get<BalanceTransactionPageResponse>(`/balance/transactions?page=${page}&size=${size}`);
  },
};
