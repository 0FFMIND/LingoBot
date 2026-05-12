package com.lingobot.learning.vocabulary.repository;

import com.lingobot.learning.vocabulary.entity.VocabularyWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 标准化单词数据访问层。
 *
 * 提供标准化单词的增删改查，
 * 核心用于单词去重和查找（通过 normalizedWord 唯一索引）。
 */
@Repository
public interface VocabularyWordRepository extends JpaRepository<VocabularyWord, Long> {

    // 根据归一化后的单词查找记录
    Optional<VocabularyWord> findByNormalizedWord(String normalizedWord);

    // 检查归一化后的单词是否已存在
    boolean existsByNormalizedWord(String normalizedWord);
}
