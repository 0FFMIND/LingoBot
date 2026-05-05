export interface RedemptionCodeDTO {
  id: number;
  code: string;
  points: number;
  isUsed: boolean;
  usedByUserId?: number;
  usedByUsername?: string;
  usedAt?: string;
  createdByUserId: number;
  createdByUsername: string;
  createdAt: string;
}

export interface CreateRedemptionCodeRequest {
  points: number;
}

export interface RedeemCodeRequest {
  code: string;
}
