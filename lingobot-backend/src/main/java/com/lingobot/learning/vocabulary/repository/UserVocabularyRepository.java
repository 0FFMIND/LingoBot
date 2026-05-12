package com.lingobot.learning.vocabulary.repository;

import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户词汇数据访问层。
 *
 * 提供用户词汇记录的增删改查、分页查询和统计查询。
 * 支持按状态、筛选类型（待复习、困难词汇）等条件查询。
 */
@Repository
public interface UserVocabularyRepository extends JpaRepository<UserVocabulary, Long> {

    // 根据用户ID和标准化单词ID查找记录
    Optional<UserVocabulary> findByUserIdAndVocabularyWordId(Long userId, Long vocabularyWordId);

    // 检查用户是否已有某个单词的学习记录
    boolean existsByUserIdAndVocabularyWordId(Long userId, Long vocabularyWordId);

    // 统计用户的词汇总数
    long countByUserId(Long userId);

    // 统计用户某个状态的词汇数量
    long countByUserIdAndStatus(Long userId, VocabularyStatus status);

    // 分页查询用户所有词汇
    @Query("SELECT uv FROM UserVocabulary uv WHERE uv.userId = :userId")
    Page<UserVocabulary> findByUserId(@Param("userId") Long userId, Pageable pageable);

    // 分页查询用户某个状态的词汇
    @Query("SELECT uv FROM UserVocabulary uv WHERE uv.userId = :userId AND uv.status = :status")
    Page<UserVocabulary> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") VocabularyStatus status,
            Pageable pageable);

    // 统计用户待复习的词汇数量（nextReviewAt <= 当前时间）
    @Query("SELECT COUNT(uv) FROM UserVocabulary uv WHERE uv.userId = :userId " +
           "AND uv.nextReviewAt IS NOT NULL AND uv.nextReviewAt <= :now")
    long countToReviewByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 分页查询用户待复习的词汇
    @Query("SELECT uv FROM UserVocabulary uv WHERE uv.userId = :userId " +
           "AND uv.nextReviewAt IS NOT NULL AND uv.nextReviewAt <= :now")
    Page<UserVocabulary> findToReviewByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // 分页查询用户的困难词汇（掌握程度 <= 0.40）
    @Query("SELECT uv FROM UserVocabulary uv WHERE uv.userId = :userId " +
           "AND uv.masteryScore <= 0.40")
    Page<UserVocabulary> findDifficultByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    // 统计用户的困难词汇数量（掌握程度 <= 0.40）
    @Query("SELECT COUNT(uv) FROM UserVocabulary uv WHERE uv.userId = :userId " +
           "AND uv.masteryScore <= 0.40")
    long countDifficultByUserId(@Param("userId") Long userId);
}
