package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 释义检查状态数据传输对象。
 *
 * 用于前端轮询异步释义检查结果，
 * 直接从数据库原生查询读取，避免 JPA 缓存导致状态滞后。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeaningCheckStatusDTO {
    // 词汇卡ID
    private Long cardId;
    // 单词
    private String word;
    // 用户猜测的释义
    private String userMeaningGuess;
    // 释义检查是否已完成
    private Boolean meaningCheckCompleted;
    // 用户释义是否正确
    private Boolean meaningIsCorrect;
    // AI 对用户释义的详细反馈
    private String meaningCheckResult;
    // AI 生成的中文例句（供后续翻译练习使用）
    private String chineseSentenceForTranslation;
}
