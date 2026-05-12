package com.lingobot.learning.vocabulary.entity;

/**
 * 词汇学习状态枚举。
 *
 * 定义单词在用户学习过程中的不同阶段，
 * 用于筛选、统计和复习调度。
 */
public enum VocabularyStatus {
    // 新单词（刚添加到词汇本，尚未开始学习）
    NEW,
    // 学习中（正在学习，尚未达到复习条件）
    LEARNING,
    // 复习中（已开始进入复习阶段）
    REVIEWING,
    // 已掌握（掌握程度达到阈值，视为已学会）
    MASTERED,
    // 已忽略（用户标记为不关注，不再参与学习和复习）
    IGNORED
}
