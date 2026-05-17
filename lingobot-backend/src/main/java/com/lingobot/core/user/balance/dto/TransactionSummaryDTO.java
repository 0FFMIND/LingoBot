package com.lingobot.core.user.balance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 交易汇总传输对象。
 *
 * 用于返回指定时间范围内的交易统计数据，
 * 包括总收入、总支出、净变化和交易记录总数。
 */
@Data
@Builder
public class TransactionSummaryDTO {

    // 总收入（所有余额增加的交易金额之和）
    private BigDecimal totalIncome;

    // 总支出（所有余额减少的交易金额之和，取绝对值）
    private BigDecimal totalExpense;

    // 净变化（总收入 - 总支出）
    private BigDecimal netChange;

    // 交易记录总数
    private long totalRecords;
}
