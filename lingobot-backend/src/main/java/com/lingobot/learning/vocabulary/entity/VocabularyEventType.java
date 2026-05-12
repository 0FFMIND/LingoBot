package com.lingobot.learning.vocabulary.entity;

/**
 * 词汇学习事件类型枚举。
 *
 * 用于标记用户与词汇交互的不同场景，
 * 便于统计和分析学习行为。
 */
public enum VocabularyEventType {
    // 首次学习新单词
    NEW_LEARNING,
    // 复习已学过的单词
    REVIEW,
    // 混合学习场景（既有新单词，又有复习）
    HYBRID
}
