package com.lingobot.learning.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 记忆抓取计划 DTO。
 *
 * 定义从各级词汇记忆中抓取记录的数量限制。
 * 记忆分为 L1（短期记忆）和 L2（长期记忆）两层：
 * - L1_RECENT：最近接触过的词，用于去重
 * - L1_WRONG：最近答错的词，用于复习优先
 * - L1_REGENERATED：最近重新生成过的词，标记用户不满意
 * - L2_MASTERED：已掌握词（掌握度>=80%）
 * - L2_REVIEWING：复习中词
 * - L2_LEARNING：学习中词
 * - L2_WEAK：薄弱词（掌握度<40%，错误>=1）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecallPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    // L1 短期记忆：最近 7 天接触过的词，主要用于避免重复推荐
    private int l1RecentLimit;

    // L1 短期记忆：最近 14 天答错的词，复习模式下优先级较高
    private int l1WrongLimit;

    // L1 短期记忆：最近 14 天重新生成过的词，捕捉用户不满意的词汇偏好
    private int l1RegeneratedLimit;

    // L2 长期记忆：已掌握词（掌握度>=80%），复习模式下通常设为 0
    private int l2MasteredLimit;

    // L2 长期记忆：复习中词，复习模式下优先级较高
    private int l2ReviewingLimit;

    // L2 长期记忆：学习中词
    private int l2LearningLimit;

    // L2 长期记忆：薄弱词（掌握度<40%，错误>=1），复习模式下优先级较高
    private int l2WeakLimit;

    // LLM 推荐的学习意图（仅在 SMART_RECOMMEND 模式下由 Agent 决策返回）
    // 可选值：new_word、review、hybrid
    private String recommendedIntent;

    // LLM 生成的规划理由，解释为什么选择这个模式和这样分配抓取数量
    private String reasoning;
}
