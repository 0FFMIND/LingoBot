package com.lingobot.learning.vocabulary.repository;

import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /** 删除对话的所有词汇卡 */
    void deleteByConversationId(Long conversationId);

    /** 获取用户某个单词的最近词汇卡（用于展示最新的内容） */
    @Query("SELECT vc FROM VocabularyCard vc WHERE vc.conversation.user.id = :userId " +
           "AND vc.vocabularyWordId = :vocabularyWordId AND vc.isRegenerated = false " +
           "ORDER BY vc.createdAt DESC")
    List<VocabularyCard> findLatestCardsByUserIdAndVocabularyWordId(
            @Param("userId") Long userId,
            @Param("vocabularyWordId") Long vocabularyWordId,
            Pageable pageable);
}
