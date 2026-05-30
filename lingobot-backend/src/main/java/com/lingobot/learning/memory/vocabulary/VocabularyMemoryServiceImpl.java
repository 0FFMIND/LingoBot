package com.lingobot.learning.memory.vocabulary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyStatus;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyMemoryServiceImpl implements VocabularyMemoryService {

    private final UserVocabularyRepository userVocabularyRepository;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final VocabularyConversationDataService vocabDataService;
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
        return retrieveMemoryWithLimits(userId, conversationId, intent,
                MAX_L1_ITEMS, MAX_L1_ITEMS, MAX_L1_ITEMS,
                MAX_MASTERED_WORDS, MAX_REVIEWING_WORDS, MAX_LEARNING_WORDS, MAX_WEAK_WORDS);
    }

    @Override
    public VocabularyMemoryContext retrieveMemoryWithLimits(Long userId, Long conversationId,
                                                             VocabularyGenerationIntent intent,
                                                             int l1RecentLimit,
                                                             int l1WrongLimit,
                                                             int l1RegeneratedLimit,
                                                             int l2MasteredLimit,
                                                             int l2ReviewingLimit,
                                                             int l2LearningLimit,
                                                             int l2WeakLimit) {
        log.debug("Retrieving vocabulary memory with limits for userId={}, conversationId={}, intent={}",
                userId, conversationId, intent);
        log.debug("Limits: l1Recent={}, l1Wrong={}, l1Regenerated={}, l2Mastered={}, l2Reviewing={}, l2Learning={}, l2Weak={}",
                l1RecentLimit, l1WrongLimit, l1RegeneratedLimit,
                l2MasteredLimit, l2ReviewingLimit, l2LearningLimit, l2WeakLimit);

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
            List<VocabularyMemoryRecord> l1Recent = retrieveL1Recent(userId, l1RecentLimit);
            List<VocabularyMemoryRecord> l1Wrong = retrieveL1Wrong(userId, l1WrongLimit);
            List<VocabularyMemoryRecord> l1Regenerated = retrieveL1Regenerated(userId, l1RegeneratedLimit);

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
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> filteredWrong = l1Wrong.stream()
                    .filter(record -> !conversationWordMap.containsKey(normalize(record.getWord())))
                    .peek(record -> {
                        if (record.getSourceTiers() == null) {
                            record.setSourceTiers(new ArrayList<>());
                        }
                        record.getSourceTiers().add(VocabularyMemoryTier.L1_WRONG);
                    })
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> filteredRegenerated = l1Regenerated.stream()
                    .filter(record -> !conversationWordMap.containsKey(normalize(record.getWord())))
                    .peek(record -> {
                        if (record.getSourceTiers() == null) {
                            record.setSourceTiers(new ArrayList<>());
                        }
                        record.getSourceTiers().add(VocabularyMemoryTier.L1_REGENERATED);
                    })
                    .collect(Collectors.toList());

            builder.recentWords(filteredRecent);
            builder.wrongWords(filteredWrong);
            builder.regeneratedWords(filteredRegenerated);

            List<VocabularyMemoryRecord> allMasteredWords = retrieveL2Mastered(userId, l2MasteredLimit);
            List<VocabularyMemoryRecord> allReviewingWords = retrieveL2Reviewing(userId, l2ReviewingLimit);
            List<VocabularyMemoryRecord> allLearningWords = retrieveL2Learning(userId, l2LearningLimit);
            List<VocabularyMemoryRecord> allWeakWords = retrieveL2Weak(userId, l2WeakLimit);

            Map<String, VocabularyMemoryRecord> l1WordMap = Stream.of(
                            filteredRecent.stream(),
                            filteredWrong.stream(),
                            filteredRegenerated.stream()
                    )
                    .flatMap(s -> s)
                    .collect(Collectors.toMap(
                            record -> normalize(record.getWord()),
                            record -> record,
                            (existing, replacement) -> existing
                    ));

            Consumer<VocabularyMemoryRecord> mergeL2ToL1 = l2Record -> {
                String normalizedWord = normalize(l2Record.getWord());
                VocabularyMemoryRecord l1Record = l1WordMap.get(normalizedWord);
                if (l1Record != null) {
                    if (l1Record.getSourceTiers() == null) {
                        l1Record.setSourceTiers(new ArrayList<>());
                    }
                    l2Record.getSourceTiers().forEach(tier -> {
                        if (!l1Record.getSourceTiers().contains(tier)) {
                            l1Record.getSourceTiers().add(tier);
                        }
                    });
                }
            };

            Predicate<VocabularyMemoryRecord> l2Filter = l2Record -> {
                String normalizedWord = normalize(l2Record.getWord());
                if (conversationWordMap.containsKey(normalizedWord)) {
                    return false;
                }
                return !l1WordMap.containsKey(normalizedWord);
            };

            List<VocabularyMemoryRecord> masteredWords = allMasteredWords.stream()
                    .peek(mergeL2ToL1)
                    .filter(l2Filter)
                    .limit(l2MasteredLimit)
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> reviewingWords = allReviewingWords.stream()
                    .peek(mergeL2ToL1)
                    .filter(l2Filter)
                    .limit(l2ReviewingLimit)
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> learningWords = allLearningWords.stream()
                    .peek(mergeL2ToL1)
                    .filter(l2Filter)
                    .limit(l2LearningLimit)
                    .collect(Collectors.toList());

            List<VocabularyMemoryRecord> weakWords = allWeakWords.stream()
                    .peek(mergeL2ToL1)
                    .filter(l2Filter)
                    .limit(l2WeakLimit)
                    .collect(Collectors.toList());

            builder.masteredWords(masteredWords);
            builder.reviewingWords(reviewingWords);
            builder.learningWords(learningWords);
            builder.weakWords(weakWords);
        }

        VocabularyMemoryContext context = builder.build();
        log.debug("Retrieved memory context with limits: totalItems={}, conversation={}, recent={}, wrong={}, regenerated={}, mastered={}, reviewing={}, learning={}, weak={}",
                context.totalMemoryItems(),
                context.getConversationRecentCards().size(),
                context.getRecentWords().size(),
                context.getWrongWords().size(),
                context.getRegeneratedWords().size(),
                context.getMasteredWords().size(),
                context.getReviewingWords().size(),
                context.getLearningWords().size(),
                context.getWeakWords().size());

        return context;
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL1Recent(Long userId, int limit) {
        return retrieveL1(userId, L1_RECENT_PREFIX, RECENT_TTL_DAYS, limit);
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL1Wrong(Long userId, int limit) {
        return retrieveL1(userId, L1_WRONG_PREFIX, WRONG_TTL_DAYS, limit);
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL1Regenerated(Long userId, int limit) {
        return retrieveL1(userId, L1_REGENERATED_PREFIX, REGENERATED_TTL_DAYS, limit);
    }

    private List<VocabularyMemoryRecord> retrieveL1(Long userId, String prefix, long ttlDays, int limit) {
        if (userId == null) return new ArrayList<>();
        List<VocabularyMemoryRecord> records = getL1Records(prefix + userId, ttlDays);
        return records.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL2Mastered(Long userId, int limit) {
        return retrieveL2(userId, limit,
                Sort.by(Sort.Direction.DESC, "masteryScore"),
                userVocabularyRepository::findL2MasteredByUserId,
                VocabularyMemoryTier.L2_MASTERED);
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL2Reviewing(Long userId, int limit) {
        return retrieveL2(userId, limit,
                Sort.by(Sort.Direction.ASC, "nextReviewAt"),
                userVocabularyRepository::findL2ReviewingByUserId,
                VocabularyMemoryTier.L2_REVIEWING);
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL2Learning(Long userId, int limit) {
        return retrieveL2(userId, limit,
                Sort.by(Sort.Direction.DESC, "updatedAt"),
                userVocabularyRepository::findL2LearningByUserId,
                VocabularyMemoryTier.L2_LEARNING);
    }

    @Override
    public List<VocabularyMemoryRecord> retrieveL2Weak(Long userId, int limit) {
        return retrieveL2(userId, limit,
                Sort.by(Sort.Direction.ASC, "masteryScore"),
                userVocabularyRepository::findL2WeakByUserId,
                VocabularyMemoryTier.L2_WEAK);
    }

    private List<VocabularyMemoryRecord> retrieveL2(Long userId, int limit, Sort sort,
                                                     L2QueryFunction queryFunction,
                                                     VocabularyMemoryTier tier) {
        if (userId == null || limit <= 0) return new ArrayList<>();
        PageRequest pageable = PageRequest.of(0, limit, sort);
        List<UserVocabulary> userVocabularies = queryFunction.query(userId, pageable);
        return userVocabularies.stream()
                .map(this::toMemoryRecord)
                .peek(record -> {
                    if (record.getSourceTiers() == null) record.setSourceTiers(new ArrayList<>());
                    record.getSourceTiers().add(tier);
                })
                .collect(Collectors.toList());
    }

    @FunctionalInterface
    private interface L2QueryFunction {
        List<UserVocabulary> query(Long userId, Pageable pageable);
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
        return vocabDataService.getByConversationId(conversationId)
                .map(learningData -> VocabularyCompactWatermark.builder()
                        .lastCompactedCardId(learningData.getVocabularyLastCompactedCardId())
                        .lastCompactedPosition(learningData.getVocabularyLastCompactedPosition())
                        .lastCompactedAt(learningData.getVocabularyLastCompactedAt())
                        .compactedCardCount(learningData.getVocabularyCompactedCardCount())
                        .build())
                .orElse(null);
    }

    private String getVocabularyCompactedSummary(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        return vocabDataService.getByConversationId(conversationId)
                .map(VocabularyConversationData::getVocabularyCompactedSummary)
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
                .filter(uv -> uv.getStatus() == VocabularyStatus.MASTERED
                        || (uv.getMasteryScore() != null && uv.getMasteryScore().compareTo(new java.math.BigDecimal("0.8")) >= 0))
                .sorted((a, b) -> b.getMasteryScore().compareTo(a.getMasteryScore()))
                .limit(MAX_MASTERED_WORDS)
                .map(this::toMemoryRecord)
                .collect(Collectors.toList());
    }

    private List<VocabularyMemoryRecord> getReviewingWords(List<UserVocabulary> vocabularies) {
        return vocabularies.stream()
                .filter(uv -> uv.getStatus() == VocabularyStatus.REVIEWING)
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
                .filter(uv -> uv.getStatus() == VocabularyStatus.LEARNING)
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

        VocabularyConversationData vocabData = vocabDataService.getByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());

        List<VocabularyCard> allCards = vocabularyCardRepository
                .findByConversationIdOrderByPositionAsc(conversationId);

        if (allCards.isEmpty()) {
            return null;
        }

        VocabularyCard lastCard = allCards.get(allCards.size() - 1);
        LocalDateTime now = LocalDateTime.now();

        int currentCompactedCount = vocabData.getVocabularyCompactedCardCount() != null
                ? vocabData.getVocabularyCompactedCardCount() : 0;
        int newlyCompactedCount = (int) allCards.stream()
                .filter(card -> vocabData.getVocabularyLastCompactedPosition() == null
                        || card.getPosition() > vocabData.getVocabularyLastCompactedPosition())
                .count();

        int newTotalCount = currentCompactedCount + newlyCompactedCount;
        vocabDataService.updateCompactedSummary(
                conversationId,
                compactedSummary,
                lastCard.getId(),
                lastCard.getPosition(),
                newTotalCount
        );

        log.info("Compacted vocabulary history for conversationId={}, lastCompactedPosition={}, compactedCardCount={}",
                conversationId, lastCard.getPosition(), newTotalCount);

        return VocabularyCompactWatermark.builder()
                .lastCompactedCardId(lastCard.getId())
                .lastCompactedPosition(lastCard.getPosition())
                .lastCompactedAt(now)
                .compactedCardCount(newTotalCount)
                .build();
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
