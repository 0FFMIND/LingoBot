package com.lingobot.core.user.balance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TransactionSummaryDTO {

    private BigDecimal totalIncome;

    private BigDecimal totalExpense;

    private BigDecimal netChange;

    private long totalRecords;
}
