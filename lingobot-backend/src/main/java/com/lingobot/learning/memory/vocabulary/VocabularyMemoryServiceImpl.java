package com.lingobot.learning.memory.vocabulary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyMemoryServiceImpl implements VocabularyMemoryService {

    private final UserVocabularyRepository userVocabularyRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String L1_RECENT_PREFIX = "memory:l1:recent:";
    private static final String L1_WRONG_PREFIX = "memory:l1:wrong:";
    private static final String L1_REGENERATED_PREFIX = "memory:l1:regenerated:";
    private static final int MAX_L1_ITEMS = 10;
    private static final long RECENT_TTL_DAYS = 7;
    private static final long WRONG_TTL_DAYS = 14;
    private static final long REGENERATED_TTL_DAYS = 14;
    private static final int MAX_RECENT_WORDS = 20;
    private static final int MAX_MASTERED_WORDS = 30;
    private static final int MAX_WEAK_WORDS = 15;

    @Override
    public VocabularyMemoryContext retrieveMemory(Long userId, Long conversationId,
                                                   VocabularyGenerationIntent intent,
                                                   VocabularyGenerationConstraints constraints) {
        log.debug("Retrieving vocabulary memory for userId={}, conversationId={}, intent={}",
                userId, conversationId, intent);

        VocabularyMemoryContext.VocabularyMemoryContextBuilder builder = VocabularyMemoryContext.builder();

        List<VocabularyMemoryRecord> conversationRecentCards = getConversationRecentCards(conversationId);
        builder.conversationRecentCards(conversationRecentCards);

        List<String> excludedWords = conversationRecentCards.stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        builder.excludedWords(excludedWords);

        if (userId != null) {
            builder.recentWords(getL1Records(L1_RECENT_PREFIX + userId));
            builder.wrongWords(getL1Records(L1_WRONG_PREFIX + userId));
            builder.regeneratedWords(getL1Records(L1_REGENERATED_PREFIX + userId));

            List<UserVocabulary> userVocabularies = userVocabularyRepository.findByUserId(userId);

            List<VocabularyMemoryRecord> masteredWords = getMasteredWords(userVocabularies);
            builder.masteredWords(masteredWords);

            List<VocabularyMemoryRecord> weakWords = getWeakWords(userVocabularies);
            builder.weakWords(weakWords);
        }

        VocabularyMemoryContext context = builder.build();
        log.debug("Retrieved memory context: totalItems={}, conversation={}, recent={}, wrong={}, regenerated={}, mastered={}, weak={}, excluded={}",
                context.totalMemoryItems(),
                context.getConversationRecentCards().size(),
                context.getRecentWords().size(),
                context.getWrongWords().size(),
                context.getRegeneratedWords().size(),
                context.getMasteredWords().size(),
                context.getWeakWords().size(),
                context.getExcludedWords().size());

        return context;
    }

    @Override
    public void recordInteraction(Long userId, VocabularyCard card, VocabularyMemoryEventType eventType) {
        if (userId == null || card == null || card.getWord() == null || card.getWord().isBlank()) {
            return;
        }

        VocabularyMemoryEventType safeEventType = eventType != null ? eventType : VocabularyMemoryEventType.UNKNOWN;
        VocabularyMemoryRecord record = toMemoryRecord(card, safeEventType);

        upsertL1Record(L1_RECENT_PREFIX + userId, record, RECENT_TTL_DAYS);

        if (safeEventType == VocabularyMemoryEventType.WRONG) {
            upsertL1Record(L1_WRONG_PREFIX + userId, record, WRONG_TTL_DAYS);
        }
        if (safeEventType == VocabularyMemoryEventType.REGENERATED) {
            upsertL1Record(L1_REGENERATED_PREFIX + userId, record, REGENERATED_TTL_DAYS);
        }
    }

    private List<VocabularyMemoryRecord> getConversationRecentCards(Long conversationId) {
        if (conversationId == null) {
            return new ArrayList<>();
        }

        List<VocabularyCard> cards = vocabularyCardRepository
                .findByConversationIdOrderByPositionAsc(conversationId);

        return cards.stream()
                .map(card -> toMemoryRecord(card, Boolean.TRUE.equals(card.getIsRegenerated())
                        ? VocabularyMemoryEventType.REGENERATED
                        : VocabularyMemoryEventType.SEEN))
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getRecentWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .sorted((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return 1;
                    if (b.getUpdatedAt() == null) return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .limit(MAX_RECENT_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getMasteredWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .filter(uv -> uv.getMasteryScore() != null && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.8")) >= 0)
                .sorted((a, b) -> b.getMasteryScore().compareTo(a.getMasteryScore()))
                .limit(MAX_MASTERED_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getWeakWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .filter(uv -> uv.getMasteryScore() != null && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.4")) < 0)
                .sorted((a, b) -> a.getMasteryScore().compareTo(b.getMasteryScore()))
                .limit(MAX_WEAK_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private VocabularyMemoryRecord toMemoryRecord(UserVocabulary uv) {
        return VocabularyMemoryRecord.builder()
                .word(uv.getWord())
                .meaning(uv.getMeaning())
                .partOfSpeech(uv.getPartOfSpeech())
                .masteryScore(uv.getMasteryScore())
                .lastReviewedAt(uv.getUpdatedAt())
                .reviewCount(uv.getSeenCount() != null ? uv.getSeenCount() : 0)
                .isMastered(uv.getMasteryScore() != null && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.8")) >= 0)
                .build();
    }

    private VocabularyMemoryRecord toMemoryRecord(VocabularyCard card, VocabularyMemoryEventType eventType) {
        return VocabularyMemoryRecord.builder()
                .word(card.getWord())
                .meaning(card.getMeaning())
                .partOfSpeech(card.getPartOfSpeech())
                .reviewCount(0)
                .isMastered(false)
                .eventType(eventType != null ? eventType : VocabularyMemoryEventType.UNKNOWN)
                .eventTimestamp(java.time.LocalDateTime.now())
                .build();
    }

    private List<VocabularyMemoryRecord> getL1Records(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<VocabularyMemoryRecord>>() {});
        } catch (Exception e) {
            log.warn("Failed to read L1 vocabulary memory: key={}", key, e);
            return new ArrayList<>();
        }
    }

    private void upsertL1Record(String key, VocabularyMemoryRecord record, long ttlDays) {
        try {
            List<VocabularyMemoryRecord> records = getL1Records(key);
            String normalizedWord = normalize(record.getWord());
            records.removeIf(existing -> normalize(existing.getWord()).equals(normalizedWord));
            records.add(0, record);
            if (records.size() > MAX_L1_ITEMS) {
                records = new ArrayList<>(records.subList(0, MAX_L1_ITEMS));
            }

            String json = objectMapper.writeValueAsString(records);
            stringRedisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);
            log.debug("Updated L1 vocabulary memory: key={}, eventType={}, word={}",
                    key, record.getEventType(), record.getWord());
        } catch (Exception e) {
            log.warn("Failed to update L1 vocabulary memory: key={}, word={}", key, record.getWord(), e);
        }
    }

    private String normalize(String word) {
        return word == null ? "" : word.trim().toLowerCase();
    }
}
