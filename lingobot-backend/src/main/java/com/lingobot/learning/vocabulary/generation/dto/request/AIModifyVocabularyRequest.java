package com.lingobot.learning.vocabulary.generation.dto.request;

import lombok.Data;

import java.util.List;

/**
 * AI修改词汇的请求体 DTO。
 *
 * 用于请求 AI 完善或修改用户词汇本中的单词信息，
 * AI 会补充缺失的字段或优化已有字段内容。
 */
@Data
public class AIModifyVocabularyRequest {
    // 用户词汇记录ID
    private Long id;
    // 单词
    private String word;
    // 音标
    private String phonetic;
    // 词性（如 n., v., adj., adv. 等）
    private String partOfSpeech;
    // 中文释义
    private String meaning;
    // 同义词列表
    private List<String> synonyms;
    // 词汇类别（如 cefr, ielts, toefl）
    private String category;
    // 难度级别（如 a1, b2, 5.5-6.5, 81-100 等）
    private String difficulty;
}
