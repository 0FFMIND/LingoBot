package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 句子分析状态数据传输对象。
 *
 * 用于前端轮询异步句子分析结果，
 * 包含 AI 对用户英文句子的分析结论和详细反馈。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentenceAnalysisStatusDTO {
    // 词汇卡ID
    private Long cardId;
    // 单词
    private String word;
    // 中文例句（供用户翻译的原文）
    private String chineseSentenceForTranslation;
    // 用户写的英文句子
    private String userEnglishSentence;
    // 句子分析是否已完成
    private Boolean sentenceAnalysisCompleted;
    // 用户句子是否包含新单词
    private Boolean sentenceHasNewWord;
    // 用户句子的意思是否与中文例句匹配
    private Boolean sentenceMeaningMatches;
    // AI 对用户句子的详细分析反馈
    private String sentenceAnalysis;
}
