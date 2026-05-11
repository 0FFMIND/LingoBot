export interface RedemptionCodeUsageDTO {
  id: number;
  redemptionCodeId: number;
  userId: number;
  username: string;
  usedAt: string;
}

export interface RedemptionCodeDTO {
  id: number;
  code: string;
  points: number;
  maxUsages?: number;
  usageCount: number;
  isFullyUsed: boolean;
  createdByUserId: number;
  createdByUsername: string;
  createdAt: string;
  expiresAt?: string;
  isExpired?: boolean;
  usages?: RedemptionCodeUsageDTO[];
}

export interface CreateRedemptionCodeRequest {
  points: number;
  expiresInSeconds?: number;
  maxUsages?: number;
}

export interface RedeemCodeRequest {
  code: string;
}
