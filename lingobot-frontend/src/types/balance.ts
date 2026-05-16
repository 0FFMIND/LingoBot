export type TransactionType = 'CHARGE' | 'RECHARGE' | 'ADMIN_ADJUSTMENT';

export interface BalanceTransactionDTO {
  publicId: string;
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

export interface TransactionSummaryDTO {
  totalIncome: number;
  totalExpense: number;
  netChange: number;
  totalRecords: number;
}

export interface TransactionFilter {
  startDate?: string;
  endDate?: string;
  type?: 'all' | 'income' | 'expense';
}
