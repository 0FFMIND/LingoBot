package com.lingobot.learning.vocabulary.service.impl;

import com.lingobot.learning.vocabulary.entity.VocabularyWord;
import com.lingobot.learning.vocabulary.repository.VocabularyWordRepository;
import com.lingobot.learning.vocabulary.service.VocabularyWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标准化单词服务实现类。
 *
 * 实现单词归一化和查找/创建功能，
 * 确保同一单词在系统中只存储一条标准化记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyWordServiceImpl implements VocabularyWordService {

    private final VocabularyWordRepository vocabularyWordRepository;

    // 将单词归一化（trim + 转小写）
    @Override
    public String normalizeWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return "";
        }
        return word.trim().toLowerCase();
    }

    // 查找或创建标准化单词记录（不存在则创建）
    @Override
    @Transactional
    public VocabularyWord findOrCreateWord(String word) {
        String normalizedWord = normalizeWord(word);
        
        if (normalizedWord.isEmpty()) {
            throw new IllegalArgumentException("Word cannot be empty");
        }

        return vocabularyWordRepository.findByNormalizedWord(normalizedWord)
                .orElseGet(() -> {
                    log.info("Creating new VocabularyWord for: {}", normalizedWord);
                    VocabularyWord newWord = VocabularyWord.builder()
                            .normalizedWord(normalizedWord)
                            .build();
                    return vocabularyWordRepository.save(newWord);
                });
    }
}
