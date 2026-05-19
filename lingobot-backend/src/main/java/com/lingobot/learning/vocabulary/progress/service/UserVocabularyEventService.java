package com.lingobot.learning.vocabulary.progress.service;

import com.lingobot.learning.vocabulary.entity.LearningEventType;
import com.lingobot.learning.vocabulary.entity.UserVocabularyEvent;

import java.math.BigDecimal;
import java.util.List;

public interface UserVocabularyEventService {

    UserVocabularyEvent recordMeaningCheckEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            String userAnswer,
            Boolean isCorrect,
            String feedback,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter);

    UserVocabularyEvent recordSentenceAnalysisEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            String userAnswer,
            Boolean meaningMatches,
            Boolean hasNewWord,
            String feedback,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter);

    UserVocabularyEvent recordSeenEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter);

    UserVocabularyEvent recordCardCompletedEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter);

    List<UserVocabularyEvent> getEventsByCardId(Long userId, Long vocabularyCardId);

    List<UserVocabularyEvent> getEventsByUserVocabularyId(Long userId, Long userVocabularyId);

    List<UserVocabularyEvent> getEventsByWordId(Long userId, Long vocabularyWordId);

    List<UserVocabularyEvent> getRecentEvents(Long userId, int days);
}
