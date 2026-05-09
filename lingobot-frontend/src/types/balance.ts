export type TransactionType = 'CHARGE' | 'RECHARGE';

export interface BalanceTransactionDTO {
  id: number;
  type: TransactionType;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
  apiCategory?: string;
  apiEndpoint?: string;
  description?: string;
  conversationId?: number;
  createdAt: string;
}

export interface BalanceTransactionPageResponse {
  content: BalanceTransactionDTO[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
