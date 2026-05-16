package com.lingobot.learning.memory.vocabulary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyMemoryServiceImpl implements VocabularyMemoryService {

    private final UserVocabularyRepository userVocabularyRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final ConversationRepository conversationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String L1_RECENT_PREFIX = "memory:l1:recent:";
    private static final String L1_WRONG_PREFIX = "memory:l1:wrong:";
    private static final String L1_REGENERATED_PREFIX = "memory:l1:regenerated:";
    private static final int MAX_L1_ITEMS = 10;
    private static final long RECENT_TTL_DAYS = 7;
    private static final long WRONG_TTL_DAYS = 14;
    private static final long REGENERATED_TTL_DAYS = 14;
    private static final int MAX_RECENT_WORDS = 10;
    private static final int MAX_MASTERED_WORDS = 10;
    private static final int MAX_LEARNING_WORDS = 10;
    private static final int MAX_REVIEWING_WORDS = 10;
    private static final int MAX_WEAK_WORDS = 10;

    @Override
    public VocabularyMemoryContext retrieveMemory(Long userId, Long conversationId,
                                                   VocabularyGenerationIntent intent,
                                                   VocabularyGenerationConstraints constraints) {
        log.debug("Retrieving vocabulary memory for userId={}, conversationId={}, intent={}",
                userId, conversationId, intent);

        VocabularyMemoryContext.VocabularyMemoryContextBuilder builder = VocabularyMemoryContext.builder();

        VocabularyCompactWatermark watermark = getVocabularyCompactWatermark(conversationId);
        builder.vocabularyCompactWatermark(watermark);
        
        String compactedSummary = getVocabularyCompactedSummary(conversationId);
        builder.vocabularyCompactedSummary(compactedSummary);

        List<VocabularyMemoryRecord> conversationRecentCards = getConversationRecentCards(conversationId, watermark);
        Map<String, VocabularyMemoryRecord> conversationWordMap = conversationRecentCards.stream()
                .filter(record -> record.getWord() != null && !record.getWord().isEmpty())
                .collect(Collectors.toMap(
                        record -> normalize(record.getWord()),
                        record -> record,
                        (existing, replacement) -> existing
                ));

        conversationRecentCards.forEach(record -> {
            if (record.getSourceTiers() == null) {
                record.setSourceTiers(new ArrayList<>());
            }
            record.getSourceTiers().add(VocabularyMemoryTier.CONVERSATION);
        });

        builder.conversationRecentCards(conversationRecentCards);

        List<String> excludedWords = conversationRecentCards.stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        builder.excludedWords(excludedWords);

        if (userId != null) {
            List<VocabularyMemoryRecord> l1Recent = getL1Records(L1_RECENT_PREFIX + userId, RECENT_TTL_DAYS);
            List<VocabularyMemoryRecord> l1Wrong = getL1Records(L1_WRONG_PREFIX + userId, WRONG_TTL_DAYS);
            List<VocabularyMemoryRecord> l1Regenerated = getL1Records(L1_REGENERATED_PREFIX + userId, REGENERATED_TTL_DAYS);

            builder.l1RecentEmpty(l1Recent.isEmpty());
            builder.l1WrongEmpty(l1Wrong.isEmpty());
            builder.l1RegeneratedEmpty(l1Regenerated.isEmpty());

            builder.l1RecentDaysSinceLastEvent(calculateDaysSinceLastEvent(l1Recent));
            builder.l1WrongDaysSinceLastEvent(calculateDaysSinceLastEvent(l1Wrong));
            builder.l1RegeneratedDaysSinceLastEvent(calculateDaysSinceLastEvent(l1Regenerated));

            List<String> mergedWords = new ArrayList<>();

            l1Recent.forEach(record -> {
                String normalizedWord = normalize(record.getWord());
                VocabularyMemoryRecord conversationRecord = conversationWordMap.get(normalizedWord);
                if (conversationRecord != null) {
                    if (conversationRecord.getSourceTiers() == null) {
                        conversationRecord.setSourceTiers(new ArrayList<>());
                    }
                    conversationRecord.getSourceTiers().add(VocabularyMemoryTier.L1_RECENT);
                    mergedWords.add(record.getWord());
                }
            });

            l1Wrong.forEach(record -> {
                String normalizedWord = normalize(record.getWord());
                VocabularyMemoryRecord conversationRecord = conversationWordMap.get(normalizedWord);
                if (conversationRecord != null) {
                    if (conversationRecord.getSourceTiers() == null) {
                        conversationRecord.setSourceTiers(new ArrayList<>());
                    }
                    conversationRecord.getSourceTiers().add(VocabularyMemoryTier.L1_WRONG);
                    mergedWords.add(record.getWord());
                }
            });

            l1Regenerated.forEach(record -> {
                String normalizedWord = normalize(record.getWord());
                VocabularyMemoryRecord conversationRecord = conversationWordMap.get(normalizedWord);
                if (conversationRecord != null) {
                    if (conversationRecord.getSourceTiers() == null) {
                        conversationRecord.setSourceTiers(new ArrayList<>());
                    }
                    conversationRecord.getSourceTiers().add(VocabularyMemoryTier.L1_REGENERATED);
                    mergedWords.add(record.getWord());
                }
            });

            builder.mergedL1ToConversationWords(mergedWords.stream().distinct().collect(Collectors.toList()));

            List<VocabularyMemoryRecord> filteredRecent = l1Recent.stream()
                    .filter(record -> !conversationWordMap.containsKey(normalize(record.getWord())))
                    .peek(record -> {
                        if (record.getSourceTiers() == null) {
                            record.setSourceTiers(new ArrayList<>());
                        }
                        record.getSourceTiers().add(VocabularyMemoryTier.L1_RECENT);
                    })
                    .limit(MAX_L1_ITEMS)
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> filteredWrong = l1Wrong.stream()
                    .filter(record -> !conversationWordMap.containsKey(normalize(record.getWord())))
                    .peek(record -> {
                        if (record.getSourceTiers() == null) {
                            record.setSourceTiers(new ArrayList<>());
                        }
                        record.getSourceTiers().add(VocabularyMemoryTier.L1_WRONG);
                    })
                    .limit(MAX_L1_ITEMS)
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> filteredRegenerated = l1Regenerated.stream()
                    .filter(record -> !conversationWordMap.containsKey(normalize(record.getWord())))
                    .peek(record -> {
                        if (record.getSourceTiers() == null) {
                            record.setSourceTiers(new ArrayList<>());
                        }
                        record.getSourceTiers().add(VocabularyMemoryTier.L1_REGENERATED);
                    })
                    .limit(MAX_L1_ITEMS)
                    .collect(Collectors.toList());

            builder.recentWords(filteredRecent);
            builder.wrongWords(filteredWrong);
            builder.regeneratedWords(filteredRegenerated);

            List<UserVocabulary> userVocabularies = userVocabularyRepository.findByUserId(userId);

            List<VocabularyMemoryRecord> masteredWords = getMasteredWords(userVocabularies);
            masteredWords.forEach(record -> {
                if (record.getSourceTiers() == null) {
                    record.setSourceTiers(new ArrayList<>());
                }
                record.getSourceTiers().add(VocabularyMemoryTier.L2_MASTERED);
            });
            builder.masteredWords(masteredWords);

            List<VocabularyMemoryRecord> reviewingWords = getReviewingWords(userVocabularies);
            reviewingWords.forEach(record -> {
                if (record.getSourceTiers() == null) {
                    record.setSourceTiers(new ArrayList<>());
                }
                record.getSourceTiers().add(VocabularyMemoryTier.L2_REVIEWING);
            });
            builder.reviewingWords(reviewingWords);

            List<VocabularyMemoryRecord> learningWords = getLearningWords(userVocabularies);
            learningWords.forEach(record -> {
                if (record.getSourceTiers() == null) {
                    record.setSourceTiers(new ArrayList<>());
                }
                record.getSourceTiers().add(VocabularyMemoryTier.L2_LEARNING);
            });
            builder.learningWords(learningWords);

            List<VocabularyMemoryRecord> weakWords = getWeakWords(userVocabularies);
            weakWords.forEach(record -> {
                if (record.getSourceTiers() == null) {
                    record.setSourceTiers(new ArrayList<>());
                }
                record.getSourceTiers().add(VocabularyMemoryTier.L2_WEAK);
            });
            builder.weakWords(weakWords);
        }

        VocabularyMemoryContext context = builder.build();
        log.debug("Retrieved memory context: totalItems={}, conversation={}, recent={}, wrong={}, regenerated={}, mastered={}, reviewing={}, learning={}, weak={}, excluded={}, merged={}",
                context.totalMemoryItems(),
                context.getConversationRecentCards().size(),
                context.getRecentWords().size(),
                context.getWrongWords().size(),
                context.getRegeneratedWords().size(),
                context.getMasteredWords().size(),
                context.getReviewingWords().size(),
                context.getLearningWords().size(),
                context.getWeakWords().size(),
                context.getExcludedWords().size(),
                context.getMergedL1ToConversationWords().size());

        return context;
    }

    @Override
    public void recordInteraction(Long userId, VocabularyCard card, VocabularyMemoryEventType eventType) {
        recordInteraction(userId, card, eventType, null, null, null);
    }

    @Override
    public void recordInteraction(Long userId, VocabularyCard card, VocabularyMemoryEventType eventType,
                                  String userAnswer, String aiFeedback, VocabularyMemoryInteractionType interactionType) {
        if (userId == null || card == null || card.getWord() == null || card.getWord().isBlank()) {
            return;
        }

        VocabularyMemoryEventType safeEventType = eventType != null ? eventType : VocabularyMemoryEventType.UNKNOWN;
        VocabularyMemoryRecord record = toMemoryRecord(card, safeEventType, userAnswer, aiFeedback, interactionType);

        upsertL1Record(L1_RECENT_PREFIX + userId, record, RECENT_TTL_DAYS);

        if (safeEventType == VocabularyMemoryEventType.WRONG) {
            upsertL1Record(L1_WRONG_PREFIX + userId, record, WRONG_TTL_DAYS);
        }
        if (safeEventType == VocabularyMemoryEventType.REGENERATED) {
            upsertL1Record(L1_REGENERATED_PREFIX + userId, record, REGENERATED_TTL_DAYS);
        }
    }

    private List<VocabularyMemoryRecord> getConversationRecentCards(Long conversationId, VocabularyCompactWatermark watermark) {
        if (conversationId == null) {
            return new ArrayList<>();
        }

        List<VocabularyCard> cards = vocabularyCardRepository
                .findByConversationIdOrderByPositionAsc(conversationId);
        
        if (watermark != null && watermark.getLastCompactedPosition() != null) {
            cards = cards.stream()
                    .filter(card -> card.getPosition() > watermark.getLastCompactedPosition())
                    .collect(Collectors.toList());
        }

        return cards.stream()
                .map(this::toMemoryRecordWithInteraction)
                .collect(Collectors.toList());
    }
    
    private VocabularyCompactWatermark getVocabularyCompactWatermark(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        return conversationRepository.findById(conversationId)
                .map(conversation -> VocabularyCompactWatermark.builder()
                        .lastCompactedCardId(conversation.getVocabularyLastCompactedCardId())
                        .lastCompactedPosition(conversation.getVocabularyLastCompactedPosition())
                        .lastCompactedAt(conversation.getVocabularyLastCompactedAt())
                        .compactedCardCount(conversation.getVocabularyCompactedCardCount())
                        .build())
                .orElse(null);
    }
    
    private String getVocabularyCompactedSummary(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        return conversationRepository.findById(conversationId)
                .map(Conversation::getVocabularyCompactedSummary)
                .orElse(null);
    }

    private VocabularyMemoryRecord toMemoryRecordWithInteraction(VocabularyCard card) {
        VocabularyMemoryEventType eventType = Boolean.TRUE.equals(card.getIsRegenerated())
                ? VocabularyMemoryEventType.REGENERATED
                : VocabularyMemoryEventType.SEEN;

        String userAnswer = null;
        String aiFeedback = null;
        VocabularyMemoryInteractionType interactionType = VocabularyMemoryInteractionType.NONE;
        String meaningCheckUserAnswer = null;
        String meaningCheckAiFeedback = null;
        Boolean meaningCheckIsCorrect = null;
        String sentenceAnalysisUserAnswer = null;
        String sentenceAnalysisAiFeedback = null;
        Boolean sentenceAnalysisIsCorrect = null;

        boolean hasMeaningCheck = Boolean.TRUE.equals(card.getMeaningCheckCompleted());
        boolean hasSentenceAnalysis = Boolean.TRUE.equals(card.getSentenceAnalysisCompleted());

        if (hasMeaningCheck) {
            if (card.getUserMeaningGuess() != null && !card.getUserMeaningGuess().isEmpty()) {
                meaningCheckUserAnswer = card.getUserMeaningGuess();
            }
            if (card.getMeaningCheckResult() != null && !card.getMeaningCheckResult().isEmpty()) {
                meaningCheckAiFeedback = card.getMeaningCheckResult();
            }
            meaningCheckIsCorrect = card.getMeaningIsCorrect();
            if (card.getMeaningIsCorrect() != null) {
                eventType = card.getMeaningIsCorrect()
                        ? VocabularyMemoryEventType.CORRECT
                        : VocabularyMemoryEventType.WRONG;
            }
            userAnswer = meaningCheckUserAnswer;
            aiFeedback = meaningCheckAiFeedback;
            interactionType = VocabularyMemoryInteractionType.MEANING_CHECK;
        }

        if (hasSentenceAnalysis) {
            if (card.getUserEnglishSentence() != null && !card.getUserEnglishSentence().isEmpty()) {
                sentenceAnalysisUserAnswer = card.getUserEnglishSentence();
            }
            if (card.getSentenceAnalysis() != null && !card.getSentenceAnalysis().isEmpty()) {
                sentenceAnalysisAiFeedback = card.getSentenceAnalysis();
            }
            sentenceAnalysisIsCorrect = card.getSentenceMeaningMatches();
            if (card.getSentenceMeaningMatches() != null && !hasMeaningCheck) {
                eventType = card.getSentenceMeaningMatches()
                        ? VocabularyMemoryEventType.CORRECT
                        : VocabularyMemoryEventType.WRONG;
            }
            if (!hasMeaningCheck) {
                userAnswer = sentenceAnalysisUserAnswer;
                aiFeedback = sentenceAnalysisAiFeedback;
                interactionType = VocabularyMemoryInteractionType.SENTENCE_ANALYSIS;
            }
        }

        VocabularyMemoryRecord record = toMemoryRecord(card, eventType, userAnswer, aiFeedback, interactionType);
        record.setMeaningCheckUserAnswer(meaningCheckUserAnswer);
        record.setMeaningCheckAiFeedback(meaningCheckAiFeedback);
        record.setMeaningCheckIsCorrect(meaningCheckIsCorrect);
        record.setSentenceAnalysisUserAnswer(sentenceAnalysisUserAnswer);
        record.setSentenceAnalysisAiFeedback(sentenceAnalysisAiFeedback);
        record.setSentenceAnalysisIsCorrect(sentenceAnalysisIsCorrect);
        return record;
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
                .filter(uv -> uv.getStatus() == com.lingobot.learning.vocabulary.entity.VocabularyStatus.MASTERED
                        || (uv.getMasteryScore() != null && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.8")) >= 0))
                .sorted((a, b) -> b.getMasteryScore().compareTo(a.getMasteryScore()))
                .limit(MAX_MASTERED_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getReviewingWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .filter(uv -> uv.getStatus() == com.lingobot.learning.vocabulary.entity.VocabularyStatus.REVIEWING)
                .sorted((a, b) -> {
                    if (a.getNextReviewAt() == null && b.getNextReviewAt() == null) return 0;
                    if (a.getNextReviewAt() == null) return 1;
                    if (b.getNextReviewAt() == null) return -1;
                    return a.getNextReviewAt().compareTo(b.getNextReviewAt());
                })
                .limit(MAX_REVIEWING_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getLearningWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .filter(uv -> uv.getStatus() == com.lingobot.learning.vocabulary.entity.VocabularyStatus.LEARNING)
                .sorted((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return 1;
                    if (b.getUpdatedAt() == null) return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .limit(MAX_LEARNING_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getWeakWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .filter(uv -> uv.getMasteryScore() != null
                        && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.4")) < 0
                        && uv.getWrongCount() != null
                        && uv.getWrongCount() >= 1)
                .sorted((a, b) -> a.getMasteryScore().compareTo(b.getMasteryScore()))
                .limit(MAX_WEAK_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private Long calculateDaysSinceLastEvent(List<VocabularyMemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.stream()
                .map(VocabularyMemoryRecord::getEventTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .map(time -> Duration.between(time, LocalDateTime.now()).toDays())
                .orElse(null);
    }

    private VocabularyMemoryRecord toMemoryRecord(UserVocabulary uv) {
        VocabularyMemoryEventType eventType = VocabularyMemoryEventType.SEEN;
        if (uv.getWrongCount() != null && uv.getWrongCount() > 0) {
            eventType = VocabularyMemoryEventType.WRONG;
        } else if (uv.getCorrectCount() != null && uv.getCorrectCount() > 0) {
            eventType = VocabularyMemoryEventType.CORRECT;
        }
        return VocabularyMemoryRecord.builder()
                .word(uv.getWord())
                .meaning(uv.getMeaning())
                .partOfSpeech(uv.getPartOfSpeech())
                .masteryScore(uv.getMasteryScore())
                .lastReviewedAt(uv.getUpdatedAt())
                .reviewCount(uv.getSeenCount() != null ? uv.getSeenCount() : 0)
                .isMastered(uv.getMasteryScore() != null && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.8")) >= 0)
                .eventType(eventType)
                .build();
    }

    private VocabularyMemoryRecord toMemoryRecord(VocabularyCard card, VocabularyMemoryEventType eventType) {
        return toMemoryRecord(card, eventType, null, null, null);
    }

    private VocabularyMemoryRecord toMemoryRecord(VocabularyCard card, VocabularyMemoryEventType eventType,
                                                  String userAnswer, String aiFeedback,
                                                  VocabularyMemoryInteractionType interactionType) {
        VocabularyMemoryRecord record = VocabularyMemoryRecord.builder()
                .word(card.getWord())
                .meaning(card.getMeaning())
                .partOfSpeech(card.getPartOfSpeech())
                .reviewCount(0)
                .isMastered(false)
                .eventType(eventType != null ? eventType : VocabularyMemoryEventType.UNKNOWN)
                .eventTimestamp(LocalDateTime.now())
                .position(card.getPosition())
                .regenerationIndex(card.getRegenerationIndex())
                .isRegenerated(card.getIsRegenerated())
                .userAnswer(userAnswer)
                .aiFeedback(aiFeedback)
                .interactionType(interactionType != null ? interactionType : VocabularyMemoryInteractionType.NONE)
                .build();

        if (interactionType == VocabularyMemoryInteractionType.MEANING_CHECK) {
            record.setMeaningCheckUserAnswer(userAnswer);
            record.setMeaningCheckAiFeedback(aiFeedback);
            record.setMeaningCheckIsCorrect(eventType == VocabularyMemoryEventType.CORRECT
                    ? Boolean.TRUE
                    : eventType == VocabularyMemoryEventType.WRONG ? Boolean.FALSE : null);
        } else if (interactionType == VocabularyMemoryInteractionType.SENTENCE_ANALYSIS) {
            record.setSentenceAnalysisUserAnswer(userAnswer);
            record.setSentenceAnalysisAiFeedback(aiFeedback);
            record.setSentenceAnalysisIsCorrect(eventType == VocabularyMemoryEventType.CORRECT
                    ? Boolean.TRUE
                    : eventType == VocabularyMemoryEventType.WRONG ? Boolean.FALSE : null);
        }

        return record;
    }

    private List<VocabularyMemoryRecord> getL1Records(String key, long ttlDays) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            List<VocabularyMemoryRecord> records = objectMapper.readValue(json, new TypeReference<List<VocabularyMemoryRecord>>() {});
            List<VocabularyMemoryRecord> activeRecords = pruneExpiredL1Records(records, ttlDays);
            if (activeRecords.size() != records.size()) {
                stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(activeRecords), ttlDays, TimeUnit.DAYS);
            }
            return activeRecords;
        } catch (Exception e) {
            log.warn("Failed to read L1 vocabulary memory: key={}", key, e);
            return new ArrayList<>();
        }
    }

    private void upsertL1Record(String key, VocabularyMemoryRecord record, long ttlDays) {
        try {
            List<VocabularyMemoryRecord> records = getL1Records(key, ttlDays);
            String normalizedWord = normalize(record.getWord());
            records.stream()
                    .filter(existing -> normalize(existing.getWord()).equals(normalizedWord))
                    .findFirst()
                    .ifPresent(existing -> mergeInteractionDetails(record, existing));
            records.removeIf(existing -> normalize(existing.getWord()).equals(normalizedWord));
            records.add(0, record);
            records = pruneExpiredL1Records(records, ttlDays);

            String json = objectMapper.writeValueAsString(records);
            stringRedisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);
            log.debug("Updated L1 vocabulary memory: key={}, eventType={}, word={}",
                    key, record.getEventType(), record.getWord());
        } catch (Exception e) {
            log.warn("Failed to update L1 vocabulary memory: key={}, word={}", key, record.getWord(), e);
        }
    }

    private void mergeInteractionDetails(VocabularyMemoryRecord target, VocabularyMemoryRecord existing) {
        if (target.getMeaningCheckUserAnswer() == null) {
            target.setMeaningCheckUserAnswer(existing.getMeaningCheckUserAnswer());
        }
        if (target.getMeaningCheckAiFeedback() == null) {
            target.setMeaningCheckAiFeedback(existing.getMeaningCheckAiFeedback());
        }
        if (target.getMeaningCheckIsCorrect() == null) {
            target.setMeaningCheckIsCorrect(existing.getMeaningCheckIsCorrect());
        }
        if (target.getSentenceAnalysisUserAnswer() == null) {
            target.setSentenceAnalysisUserAnswer(existing.getSentenceAnalysisUserAnswer());
        }
        if (target.getSentenceAnalysisAiFeedback() == null) {
            target.setSentenceAnalysisAiFeedback(existing.getSentenceAnalysisAiFeedback());
        }
        if (target.getSentenceAnalysisIsCorrect() == null) {
            target.setSentenceAnalysisIsCorrect(existing.getSentenceAnalysisIsCorrect());
        }
    }

    private List<VocabularyMemoryRecord> pruneExpiredL1Records(List<VocabularyMemoryRecord> records, long ttlDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(ttlDays);
        return records.stream()
                .filter(record -> record.getEventTimestamp() == null || !record.getEventTimestamp().isBefore(cutoff))
                .sorted(Comparator.comparing(
                        VocabularyMemoryRecord::getEventTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .collect(Collectors.toList());
    }

    private String normalize(String word) {
        return word == null ? "" : word.trim().toLowerCase();
    }
    
    @Override
    public VocabularyCompactWatermark compactVocabularyHistory(Long conversationId, String compactedSummary) {
        if (conversationId == null) {
            return null;
        }
        
        return conversationRepository.findById(conversationId)
                .map(conversation -> {
                    List<VocabularyCard> allCards = vocabularyCardRepository
                            .findByConversationIdOrderByPositionAsc(conversationId);
                    
                    if (allCards.isEmpty()) {
                        return null;
                    }
                    
                    VocabularyCard lastCard = allCards.get(allCards.size() - 1);
                    LocalDateTime now = LocalDateTime.now();
                    
                    int currentCompactedCount = conversation.getVocabularyCompactedCardCount() != null 
                            ? conversation.getVocabularyCompactedCardCount() : 0;
                    int newlyCompactedCount = (int) allCards.stream()
                            .filter(card -> conversation.getVocabularyLastCompactedPosition() == null 
                                    || card.getPosition() > conversation.getVocabularyLastCompactedPosition())
                            .count();
                    
                    conversation.setVocabularyCompactedSummary(compactedSummary);
                    conversation.setVocabularyLastCompactedCardId(lastCard.getId());
                    conversation.setVocabularyLastCompactedPosition(lastCard.getPosition());
                    conversation.setVocabularyLastCompactedAt(now);
                    conversation.setVocabularyCompactedCardCount(currentCompactedCount + newlyCompactedCount);
                    
                    conversationRepository.save(conversation);
                    
                    log.info("Compacted vocabulary history for conversationId={}, lastCompactedPosition={}, compactedCardCount={}",
                            conversationId, lastCard.getPosition(), currentCompactedCount + newlyCompactedCount);
                    
                    return VocabularyCompactWatermark.builder()
                            .lastCompactedCardId(lastCard.getId())
                            .lastCompactedPosition(lastCard.getPosition())
                            .lastCompactedAt(now)
                            .compactedCardCount(currentCompactedCount + newlyCompactedCount)
                            .build();
                })
                .orElse(null);
    }
    
    @Override
    public VocabularyCompactWatermark getCompactWatermark(Long conversationId) {
        return getVocabularyCompactWatermark(conversationId);
    }
    
    @Override
    public String getCompactedSummary(Long conversationId) {
        return getVocabularyCompactedSummary(conversationId);
    }
}
