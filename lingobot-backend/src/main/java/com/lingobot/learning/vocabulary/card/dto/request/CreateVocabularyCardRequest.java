package com.lingobot.learning.vocabulary.card.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建词汇卡的请求体 DTO。
 *
 * 用于手动创建词汇卡时接收前端传入的单词信息，
 * 包含单词、音标、释义、例句、同义词、词汇类别和难度级别等字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVocabularyCardRequest {

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
