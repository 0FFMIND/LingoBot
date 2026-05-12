package com.lingobot.learning.vocabulary.service;

import com.lingobot.learning.vocabulary.entity.VocabularyWord;

/**
 * 标准化单词服务接口。
 *
 * 提供单词归一化和查找/创建功能，
 * 确保同一单词在系统中只存储一条标准化记录。
 */
public interface VocabularyWordService {

    // 将单词归一化（trim + 转小写）
    String normalizeWord(String word);

    // 查找或创建标准化单词记录（不存在则创建）
    VocabularyWord findOrCreateWord(String word);
}
