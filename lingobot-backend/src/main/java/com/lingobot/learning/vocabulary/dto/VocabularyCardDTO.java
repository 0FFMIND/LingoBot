package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 词汇卡数据传输对象。
 *
 * 将 VocabularyCard 实体转换为前端友好格式，
 * 包含单词信息、用户交互记录、AI反馈和导航信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyCardDTO {

    // 词汇卡ID
    private Long id;
    // 所属对话ID
    private Long conversationId;
    // 单词
    private String word;
    // 音标
    private String phonetic;
    // 中文释义
    private String meaning;
    // 英文例句
    private String example;
    // 例句中文翻译
    private String exampleTranslation;
    // 同义词列表
    private List<String> synonyms;
    // 词汇类别（如 cefr, ielts, toefl）
    private String category;
    // 难度级别（如 A1, B2, C1, beginner, intermediate 等）
    private String difficulty;
    // 在对话中的位置顺序
    private Integer position;
    // 用户猜测的释义
    private String userMeaningGuess;
    // AI对用户释义的检查结果（详细反馈）
    private String meaningCheckResult;
    // 用户输入的释义是否正确
    private Boolean meaningIsCorrect;
    // 释义检查是否已完成
    private Boolean meaningCheckCompleted;
    // 用户造的句子
    private String userSentence;
    // AI对用户造句的反馈
    private String aiFeedback;
    // 是否已完成学习
    private Boolean isCompleted;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;

    // 是否已被重新生成（true表示已被替换，前端不展示）
    private Boolean isRegenerated;
    // 重新生成次数索引（0=原始，1=第一次重新生成...）
    private Integer regenerationIndex;
    // 该位置被重新生成过的历史单词列表（用于展示用户不满意的单词）
    private List<String> regeneratedWords;

    // 是否有上一个词汇卡（导航用）
    private Boolean hasPrev;
    // 是否有下一个词汇卡（导航用）
    private Boolean hasNext;
    // 对话中有效词汇卡总数（导航用）
    private Integer totalCount;
    // 当前索引位置（导航用）
    private Integer currentIndex;
}
