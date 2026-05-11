import { httpClient } from './httpClient';
import { BalanceTransactionDTO, BalanceTransactionPageResponse, TransactionSummaryDTO } from '../types';

export const balanceService = {
  getBalance: async (): Promise<number> => {
    return httpClient.get<number>('/redemption/balance');
  },

  getTransactions: async (
    page: number = 0,
    size: number = 50,
    type: 'all' | 'income' | 'expense' = 'all',
    startDate?: string,
    endDate?: string
  ): Promise<BalanceTransactionPageResponse> => {
    let url = `/balance/transactions?page=${page}&size=${size}&type=${type}`;
    if (startDate && endDate) {
      url += `&startDate=${startDate}&endDate=${endDate}`;
    }
    return httpClient.get<BalanceTransactionPageResponse>(url);
  },

  getTransactionSummary: async (
    startDate?: string,
    endDate?: string
  ): Promise<TransactionSummaryDTO> => {
    let url = '/balance/transactions/summary';
    if (startDate && endDate) {
      url += `?startDate=${startDate}&endDate=${endDate}`;
    }
    return httpClient.get<TransactionSummaryDTO>(url);
  },
};
