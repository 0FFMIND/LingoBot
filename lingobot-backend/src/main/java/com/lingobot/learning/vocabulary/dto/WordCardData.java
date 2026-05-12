package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 生成的单词卡片数据。
 *
 * 用于解析 AI 工具返回的单词信息，
 * 包含单词、音标、释义、例句等核心学习内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordCardData {
    // 单词
    private String word;
    // 音标
    private String phonetic;
    // 词性（如 n., v., adj., adv. 等）
    private String partOfSpeech;
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
    // 难度级别（如 a1, b2, 5.5-6.5, 81-100 等）
    private String difficulty;
}
