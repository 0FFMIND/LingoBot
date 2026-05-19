package com.lingobot.learning.vocabulary.repository;

import com.lingobot.learning.vocabulary.common.dto.ConversationOverviewDTO;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 词汇卡数据访问层。
 *
 * 提供词汇卡的增删改查、导航查询等数据库操作。
 */
@Repository
public interface VocabularyCardRepository extends JpaRepository<VocabularyCard, Long> {

    /** 按位置顺序获取对话的所有词汇卡（包括已重新生成的，用于历史记录）*/
    List<VocabularyCard> findByConversationIdOrderByPositionAsc(Long conversationId);

    /** 按位置和重新生成索引获取对话的所有词汇卡（包括已重新生成的） */
    List<VocabularyCard> findByConversationIdAndPositionOrderByRegenerationIndexAsc(Long conversationId, Integer position);

    /** 获取对话中所有有效词汇卡（未被重新生成的，isRegenerated=false），按位置排序*/
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false ORDER BY v.position ASC")
    List<VocabularyCard> findActiveCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 按位置范围获取对话的有效词汇卡（isRegenerated=false）*/
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.position BETWEEN :startPos AND :endPos ORDER BY v.position ASC")
    List<VocabularyCard> findActiveCardsByPositionRange(@Param("conversationId") Long conversationId, @Param("startPos") Integer startPos, @Param("endPos") Integer endPos);

    /** 获取对话中有效词汇卡的最大位置（用于判断是否是最后一张） */
    @Query("SELECT MAX(v.position) FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false")
    Optional<Integer> findMaxActivePositionByConversationId(@Param("conversationId") Long conversationId);

    /** 获取对话中某个位置的当前有效词汇卡（isRegenerated=false）*/
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.position = :position AND v.isRegenerated = false")
    Optional<VocabularyCard> findActiveCardByConversationIdAndPosition(@Param("conversationId") Long conversationId, @Param("position") Integer position);

    /** 获取对话中最大的位置值（用于计算新词汇卡的位置） */
    @Query("SELECT MAX(v.position) FROM VocabularyCard v WHERE v.conversation.id = :conversationId")
    Optional<Integer> findMaxPositionByConversationId(@Param("conversationId") Long conversationId);

    /** 统计对话的词汇卡数量 */
    @Query("SELECT COUNT(v) FROM VocabularyCard v WHERE v.conversation.id = :conversationId")
    long countByConversationId(@Param("conversationId") Long conversationId);

    /** 统计对话中有效词汇卡的数量*/
    @Query("SELECT COUNT(v) FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false")
    long countActiveCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 获取对话中所有未完成的词汇卡（按位置排序）*/
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isCompleted = false AND v.isRegenerated = false ORDER BY v.position ASC")
    List<VocabularyCard> findIncompleteByConversationId(@Param("conversationId") Long conversationId);

    // AI tool execution and async service can touch the same card in one flow.
    // Use a new transaction and a direct update so stale managed entities cannot flush old status values back.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            UPDATE vocabulary_cards
            SET meaning_is_correct = :isCorrect,
                meaning_check_completed = true,
                meaning_check_result = :feedback,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :cardId
            """, nativeQuery = true)
    int updateMeaningCheckResult(
            @Param("cardId") Long cardId,
            @Param("isCorrect") Boolean isCorrect,
            @Param("feedback") String feedback);

    // Same reason as updateMeaningCheckResult; this variant also updates the generated Chinese sentence.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            UPDATE vocabulary_cards
            SET meaning_is_correct = :isCorrect,
                meaning_check_completed = true,
                meaning_check_result = :feedback,
                chinese_sentence_for_translation = :chineseSentenceForTranslation,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :cardId
            """, nativeQuery = true)
    int updateMeaningCheckResultWithChineseSentence(
            @Param("cardId") Long cardId,
            @Param("isCorrect") Boolean isCorrect,
            @Param("feedback") String feedback,
            @Param("chineseSentenceForTranslation") String chineseSentenceForTranslation);

    // Sentence analysis has the same async/tool double-write risk as meaning checks.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    // Polling must read the same columns the native update writes.
    // Reading through a JPA entity can observe stale managed state in this async flow.
    @Query(value = """
            UPDATE vocabulary_cards
            SET sentence_meaning_matches = :meaningMatches,
                sentence_has_new_word = :hasNewWord,
                sentence_analysis_completed = true,
                sentence_analysis = :analysis,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :cardId
            """, nativeQuery = true)
    int updateSentenceAnalysisResult(
            @Param("cardId") Long cardId,
            @Param("meaningMatches") Boolean meaningMatches,
            @Param("hasNewWord") Boolean hasNewWord,
            @Param("analysis") String analysis);

    interface MeaningCheckStatusProjection {
        Long getCardId();
        String getWord();
        String getUserMeaningGuess();
        Boolean getMeaningCheckCompleted();
        Boolean getMeaningIsCorrect();
        String getMeaningCheckResult();
        String getChineseSentenceForTranslation();
    }

    @Query(value = """
            SELECT id AS cardId,
                   word AS word,
                   user_meaning_guess AS userMeaningGuess,
                   meaning_check_completed AS meaningCheckCompleted,
                   meaning_is_correct AS meaningIsCorrect,
                   meaning_check_result AS meaningCheckResult,
                   chinese_sentence_for_translation AS chineseSentenceForTranslation
            FROM vocabulary_cards
            WHERE id = :cardId
            """, nativeQuery = true)
    Optional<MeaningCheckStatusProjection> findMeaningCheckStatusByCardId(@Param("cardId") Long cardId);

    /** 删除对话的所有词汇卡 */
    interface CardLearningContextProjection {
        Long getCardId();
        Long getConversationId();
        Long getUserId();
        Long getVocabularyWordId();
    }

    @Query(value = """
            SELECT vc.id AS cardId,
                   vc.conversation_id AS conversationId,
                   c.user_id AS userId,
                   vc.vocabulary_word_id AS vocabularyWordId
            FROM vocabulary_cards vc
            JOIN conversations c ON c.id = vc.conversation_id
            WHERE vc.id = :cardId
            """, nativeQuery = true)
    Optional<CardLearningContextProjection> findLearningContextByCardId(@Param("cardId") Long cardId);

    void deleteByConversationId(Long conversationId);

    /** 获取用户某个单词的最近词汇卡（用于展示最新的内容） */
    @Query("SELECT vc FROM VocabularyCard vc WHERE vc.conversation.user.id = :userId " +
           "AND vc.vocabularyWordId = :vocabularyWordId AND vc.isRegenerated = false " +
           "ORDER BY vc.createdAt DESC")
    List<VocabularyCard> findLatestCardsByUserIdAndVocabularyWordId(
            @Param("userId") Long userId,
            @Param("vocabularyWordId") Long vocabularyWordId,
            Pageable pageable);

    /** 获取对话中所有已揭露的有效词汇卡 */
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.isRevealed = true ORDER BY v.position ASC")
    List<VocabularyCard> findRevealedCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 获取对话中所有未揭露的有效词汇卡 */
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.isRevealed = false ORDER BY v.position ASC")
    List<VocabularyCard> findHiddenCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 统计对话中已揭露的有效词汇卡数量 */
    @Query("SELECT COUNT(v) FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.isRevealed = true")
    long countRevealedCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 统计对话中未揭露的有效词汇卡数量 */
    @Query("SELECT COUNT(v) FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.isRevealed = false")
    long countHiddenCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 统计对话中已完成学习的有效词汇卡数量 */
    @Query("SELECT COUNT(v) FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.isCompleted = true")
    long countCompletedCardsByConversationId(@Param("conversationId") Long conversationId);

    /** 获取对话统计概览（一次性查询所有统计数据） */
    default ConversationOverviewDTO getConversationOverview(Long conversationId) {
        return ConversationOverviewDTO.builder()
                .activeCount(countActiveCardsByConversationId(conversationId))
                .revealedCount(countRevealedCardsByConversationId(conversationId))
                .hiddenCount(countHiddenCardsByConversationId(conversationId))
                .completedCount(countCompletedCardsByConversationId(conversationId))
                .build();
    }

    /** 更新卡片为已揭露状态 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE VocabularyCard v SET v.isRevealed = true, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :cardId")
    int markAsRevealed(@Param("cardId") Long cardId);

    /** 获取对话中下一个未揭露的有效词汇卡（按位置排序） */
    @Query("SELECT v FROM VocabularyCard v WHERE v.conversation.id = :conversationId AND v.isRegenerated = false AND v.isRevealed = false ORDER BY v.position ASC")
    List<VocabularyCard> findNextHiddenCard(@Param("conversationId") Long conversationId, Pageable pageable);
}
