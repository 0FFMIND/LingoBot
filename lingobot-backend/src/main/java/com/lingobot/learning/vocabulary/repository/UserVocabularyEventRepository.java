package com.lingobot.learning.vocabulary.repository;

import com.lingobot.learning.vocabulary.entity.LearningEventType;
import com.lingobot.learning.vocabulary.entity.UserVocabularyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserVocabularyEventRepository extends JpaRepository<UserVocabularyEvent, Long> {

    List<UserVocabularyEvent> findByUserIdAndVocabularyCardIdOrderByCreatedAtAsc(
            Long userId, Long vocabularyCardId);

    List<UserVocabularyEvent> findByUserIdAndUserVocabularyIdOrderByCreatedAtAsc(
            Long userId, Long userVocabularyId);

    List<UserVocabularyEvent> findByUserIdAndVocabularyWordIdOrderByCreatedAtAsc(
            Long userId, Long vocabularyWordId);

    @Query("SELECT e FROM UserVocabularyEvent e WHERE e.userId = :userId AND e.createdAt >= :since ORDER BY e.createdAt ASC")
    List<UserVocabularyEvent> findRecentEventsByUserId(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since);

    @Query("SELECT e FROM UserVocabularyEvent e WHERE e.userId = :userId AND e.vocabularyWordId = :vocabularyWordId AND e.eventType = :eventType ORDER BY e.createdAt DESC")
    List<UserVocabularyEvent> findLatestByUserIdAndVocabularyWordIdAndEventType(
            @Param("userId") Long userId,
            @Param("vocabularyWordId") Long vocabularyWordId,
            @Param("eventType") LearningEventType eventType,
            org.springframework.data.domain.Pageable pageable);
}
