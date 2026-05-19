package com.lingobot.learning.vocabulary.progress.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.learning.vocabulary.entity.LearningEventType;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.UserVocabularyEvent;
import com.lingobot.learning.vocabulary.progress.service.UserVocabularyEventService;
import com.lingobot.learning.vocabulary.repository.UserVocabularyEventRepository;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVocabularyEventServiceImpl implements UserVocabularyEventService {

    private final UserVocabularyEventRepository eventRepository;
    private final UserVocabularyRepository userVocabularyRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_EVENTS_PREFIX = "vocabulary:events:";
    private static final String REDIS_RECENT_CARDS_PREFIX = "vocabulary:recent_cards:";
    private static final long TTL_DAYS = 7;

    @Override
    @Transactional
    public UserVocabularyEvent recordMeaningCheckEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            String userAnswer,
            Boolean isCorrect,
            String feedback,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter) {

        UserVocabularyEvent event = UserVocabularyEvent.builder()
                .userId(userId)
                .vocabularyWordId(vocabularyWordId)
                .userVocabularyId(userVocabularyId)
                .vocabularyCardId(vocabularyCardId)
                .eventType(LearningEventType.MEANING_CHECK)
                .isCorrect(isCorrect)
                .scoreDelta(calculateDelta(masteryScoreBefore, masteryScoreAfter))
                .masteryScoreBefore(masteryScoreBefore)
                .masteryScoreAfter(masteryScoreAfter)
                .meaningUserAnswer(userAnswer)
                .meaningIsCorrect(isCorrect)
                .meaningFeedback(feedback)
                .build();

        UserVocabularyEvent saved = eventRepository.save(event);
        cacheEvent(userId, saved);
        cacheRecentCard(userId, vocabularyCardId);
        log.info("Recorded meaning check event: userId={}, cardId={}, isCorrect={}",
                userId, vocabularyCardId, isCorrect);
        return saved;
    }

    @Override
    @Transactional
    public UserVocabularyEvent recordSentenceAnalysisEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            String userAnswer,
            Boolean meaningMatches,
            Boolean hasNewWord,
            String feedback,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter) {

        Boolean overallCorrect = meaningMatches != null && hasNewWord != null
                ? meaningMatches && hasNewWord
                : null;

        UserVocabularyEvent event = UserVocabularyEvent.builder()
                .userId(userId)
                .vocabularyWordId(vocabularyWordId)
                .userVocabularyId(userVocabularyId)
                .vocabularyCardId(vocabularyCardId)
                .eventType(LearningEventType.SENTENCE_ANALYSIS)
                .isCorrect(overallCorrect)
                .scoreDelta(calculateDelta(masteryScoreBefore, masteryScoreAfter))
                .masteryScoreBefore(masteryScoreBefore)
                .masteryScoreAfter(masteryScoreAfter)
                .sentenceUserAnswer(userAnswer)
                .sentenceHasNewWord(hasNewWord)
                .sentenceMeaningMatches(meaningMatches)
                .sentenceFeedback(feedback)
                .build();

        UserVocabularyEvent saved = eventRepository.save(event);
        cacheEvent(userId, saved);
        cacheRecentCard(userId, vocabularyCardId);
        log.info("Recorded sentence analysis event: userId={}, cardId={}, meaningMatches={}, hasNewWord={}",
                userId, vocabularyCardId, meaningMatches, hasNewWord);
        return saved;
    }

    @Override
    @Transactional
    public UserVocabularyEvent recordSeenEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter) {

        UserVocabularyEvent event = UserVocabularyEvent.builder()
                .userId(userId)
                .vocabularyWordId(vocabularyWordId)
                .userVocabularyId(userVocabularyId)
                .vocabularyCardId(vocabularyCardId)
                .eventType(LearningEventType.SEEN)
                .scoreDelta(calculateDelta(masteryScoreBefore, masteryScoreAfter))
                .masteryScoreBefore(masteryScoreBefore)
                .masteryScoreAfter(masteryScoreAfter)
                .build();

        UserVocabularyEvent saved = eventRepository.save(event);
        cacheEvent(userId, saved);
        cacheRecentCard(userId, vocabularyCardId);
        log.info("Recorded seen event: userId={}, cardId={}", userId, vocabularyCardId);
        return saved;
    }

    @Override
    @Transactional
    public UserVocabularyEvent recordCardCompletedEvent(
            Long userId,
            Long vocabularyWordId,
            Long userVocabularyId,
            Long vocabularyCardId,
            BigDecimal masteryScoreBefore,
            BigDecimal masteryScoreAfter) {

        UserVocabularyEvent event = UserVocabularyEvent.builder()
                .userId(userId)
                .vocabularyWordId(vocabularyWordId)
                .userVocabularyId(userVocabularyId)
                .vocabularyCardId(vocabularyCardId)
                .eventType(LearningEventType.CARD_COMPLETED)
                .scoreDelta(calculateDelta(masteryScoreBefore, masteryScoreAfter))
                .masteryScoreBefore(masteryScoreBefore)
                .masteryScoreAfter(masteryScoreAfter)
                .build();

        UserVocabularyEvent saved = eventRepository.save(event);
        cacheEvent(userId, saved);
        cacheRecentCard(userId, vocabularyCardId);
        log.info("Recorded card completed event: userId={}, cardId={}", userId, vocabularyCardId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserVocabularyEvent> getEventsByCardId(Long userId, Long vocabularyCardId) {
        List<UserVocabularyEvent> cached = getCachedEvents(userId, vocabularyCardId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return eventRepository.findByUserIdAndVocabularyCardIdOrderByCreatedAtAsc(userId, vocabularyCardId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserVocabularyEvent> getEventsByUserVocabularyId(Long userId, Long userVocabularyId) {
        return eventRepository.findByUserIdAndUserVocabularyIdOrderByCreatedAtAsc(userId, userVocabularyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserVocabularyEvent> getEventsByWordId(Long userId, Long vocabularyWordId) {
        return eventRepository.findByUserIdAndVocabularyWordIdOrderByCreatedAtAsc(userId, vocabularyWordId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserVocabularyEvent> getRecentEvents(Long userId, int days) {
        return eventRepository.findRecentEventsByUserId(userId, LocalDateTime.now().minusDays(days));
    }

    private BigDecimal calculateDelta(BigDecimal before, BigDecimal after) {
        if (before == null || after == null) {
            return null;
        }
        return after.subtract(before);
    }

    private void cacheEvent(Long userId, UserVocabularyEvent event) {
        try {
            String cardKey = REDIS_EVENTS_PREFIX + userId + ":card:" + event.getVocabularyCardId();
            String json = stringRedisTemplate.opsForValue().get(cardKey);
            List<UserVocabularyEvent> events;
            if (json != null && !json.isEmpty()) {
                events = objectMapper.readValue(json, new TypeReference<List<UserVocabularyEvent>>() {});
            } else {
                events = new java.util.ArrayList<>();
            }
            events.add(event);
            String newJson = objectMapper.writeValueAsString(events);
            stringRedisTemplate.opsForValue().set(cardKey, newJson, TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to cache event: userId={}, cardId={}", userId, event.getVocabularyCardId(), e);
        }
    }

    private void cacheRecentCard(Long userId, Long cardId) {
        try {
            String key = REDIS_RECENT_CARDS_PREFIX + userId;
            String json = stringRedisTemplate.opsForValue().get(key);
            List<Long> cardIds;
            if (json != null && !json.isEmpty()) {
                cardIds = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            } else {
                cardIds = new java.util.ArrayList<>();
            }
            cardIds.remove(cardId);
            cardIds.add(0, cardId);
            if (cardIds.size() > 100) {
                cardIds = cardIds.subList(0, 100);
            }
            String newJson = objectMapper.writeValueAsString(cardIds);
            stringRedisTemplate.opsForValue().set(key, newJson, TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to cache recent card: userId={}, cardId={}", userId, cardId, e);
        }
    }

    private List<UserVocabularyEvent> getCachedEvents(Long userId, Long cardId) {
        try {
            String key = REDIS_EVENTS_PREFIX + userId + ":card:" + cardId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, new TypeReference<List<UserVocabularyEvent>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to get cached events: userId={}, cardId={}", userId, cardId, e);
        }
        return null;
    }

    public Long getOrCreateUserVocabularyId(Long userId, Long vocabularyWordId) {
        Optional<UserVocabulary> uv = userVocabularyRepository.findByUserIdAndVocabularyWordId(userId, vocabularyWordId);
        return uv.map(UserVocabulary::getId).orElse(null);
    }
}
