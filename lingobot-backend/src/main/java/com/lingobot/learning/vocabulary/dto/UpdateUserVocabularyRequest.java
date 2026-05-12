package com.lingobot.learning.vocabulary.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新用户词汇的请求体 DTO。
 *
 * 用于手动更新用户词汇本中的单词信息，
 * 包含单词、音标、释义、同义词等所有可编辑字段。
 */
@Data
public class UpdateUserVocabularyRequest {
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
