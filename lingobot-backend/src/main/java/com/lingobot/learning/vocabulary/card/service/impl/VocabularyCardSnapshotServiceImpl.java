package com.lingobot.learning.vocabulary.card.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.learning.vocabulary.card.dto.response.VocabularyCardSnapshot;
import com.lingobot.learning.vocabulary.card.service.VocabularyCardSnapshotService;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCardSnapshotServiceImpl implements VocabularyCardSnapshotService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final VocabularyCardRepository vocabularyCardRepository;
    private final UserVocabularyRepository userVocabularyRepository;

    private static final String REDIS_CARD_SNAPSHOT_PREFIX = "vocabulary:snapshot:card:";
    private static final String REDIS_RECENT_CARDS_IDS_PREFIX = "vocabulary:snapshot:recent_ids:";
    private static final long TTL_DAYS = 7;
    private static final int MAX_RECENT_CARDS = 100;

    @Override
    public void saveCardSnapshot(VocabularyCard card) {
        log.warn("saveCardSnapshot(VocabularyCard) without userId is deprecated and may cause lazy loading issues");
        saveCardSnapshot(null, card);
    }

    @Override
    public void saveCardSnapshot(Long userId, VocabularyCard card) {
        VocabularyCardSnapshot snapshot = toSnapshot(userId, card);
        saveCardSnapshot(snapshot);
    }

    @Override
    public void saveCardSnapshot(VocabularyCardSnapshot snapshot) {
        if (snapshot.getUserId() == null || snapshot.getId() == null) {
            log.warn("Cannot save snapshot - missing userId or cardId");
            return;
        }

        try {
            String key = getCardSnapshotKey(snapshot.getUserId(), snapshot.getId());
            String json = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(key, json, TTL_DAYS, TimeUnit.DAYS);
            log.debug("Saved card snapshot: userId={}, cardId={}", snapshot.getUserId(), snapshot.getId());
        } catch (Exception e) {
            log.warn("Failed to save card snapshot: userId={}, cardId={}", snapshot.getUserId(), snapshot.getId(), e);
        }
    }

    @Override
    public Optional<VocabularyCardSnapshot> getCardSnapshot(Long userId, Long cardId) {
        try {
            String key = getCardSnapshotKey(userId, cardId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                VocabularyCardSnapshot snapshot = objectMapper.readValue(json, VocabularyCardSnapshot.class);
                log.debug("Retrieved card snapshot from cache: userId={}, cardId={}", userId, cardId);
                return Optional.of(snapshot);
            }
        } catch (Exception e) {
            log.warn("Failed to get card snapshot from cache: userId={}, cardId={}", userId, cardId, e);
        }

        log.debug("Card snapshot not in cache, loading from DB: userId={}, cardId={}", userId, cardId);
        return vocabularyCardRepository.findById(cardId)
                .map(card -> toSnapshot(userId, card))
                .map(snapshot -> {
                    saveCardSnapshot(snapshot);
                    return snapshot;
                });
    }

    @Override
    public List<VocabularyCardSnapshot> getRecentCardSnapshots(Long userId) {
        return getRecentCardSnapshots(userId, MAX_RECENT_CARDS);
    }

    @Override
    public List<VocabularyCardSnapshot> getRecentCardSnapshots(Long userId, int limit) {
        List<Long> recentIds = getRecentCardIds(userId);
        if (recentIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<VocabularyCardSnapshot> snapshots = new ArrayList<>();
        int actualLimit = Math.min(limit, recentIds.size());

        for (int i = 0; i < actualLimit; i++) {
            Long cardId = recentIds.get(i);
            Optional<VocabularyCardSnapshot> snapshot = getCardSnapshot(userId, cardId);
            snapshot.ifPresent(snapshots::add);
        }

        return snapshots;
    }

    @Override
    public void markCardAsRecent(Long userId, Long cardId) {
        if (userId == null || cardId == null) {
            return;
        }

        try {
            String key = getRecentIdsKey(userId);
            String json = stringRedisTemplate.opsForValue().get(key);
            List<Long> cardIds;

            if (json != null && !json.isEmpty()) {
                cardIds = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            } else {
                cardIds = new ArrayList<>();
            }

            cardIds.remove(cardId);
            cardIds.add(0, cardId);

            if (cardIds.size() > MAX_RECENT_CARDS) {
                cardIds = cardIds.subList(0, MAX_RECENT_CARDS);
            }

            String newJson = objectMapper.writeValueAsString(cardIds);
            stringRedisTemplate.opsForValue().set(key, newJson, TTL_DAYS, TimeUnit.DAYS);
            log.debug("Marked card as recent: userId={}, cardId={}, total recent={}", userId, cardId, cardIds.size());
        } catch (Exception e) {
            log.warn("Failed to mark card as recent: userId={}, cardId={}", userId, cardId, e);
        }
    }

    @Override
    public void invalidateCardSnapshot(Long userId, Long cardId) {
        try {
            String key = getCardSnapshotKey(userId, cardId);
            stringRedisTemplate.delete(key);
            log.debug("Invalidated card snapshot: userId={}, cardId={}", userId, cardId);
        } catch (Exception e) {
            log.warn("Failed to invalidate card snapshot: userId={}, cardId={}", userId, cardId, e);
        }
    }

    @Override
    public void invalidateAllUserSnapshots(Long userId) {
        try {
            String recentKey = getRecentIdsKey(userId);
            String snapshotPattern = REDIS_CARD_SNAPSHOT_PREFIX + userId + ":*";

            stringRedisTemplate.delete(recentKey);

            var keys = stringRedisTemplate.keys(snapshotPattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }

            log.debug("Invalidated all user snapshots: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to invalidate all user snapshots: userId={}", userId, e);
        }
    }

    @Override
    public VocabularyCardSnapshot toSnapshot(Long userId, VocabularyCard card) {
        Long conversationId = card.getConversation() != null
                ? card.getConversation().getId()
                : null;

        Long vocabularyWordId = card.getVocabularyWordId();
        Long userVocabularyId = null;
        BigDecimal masteryScore = null;

        if (userId != null && vocabularyWordId != null) {
            Optional<UserVocabulary> uv = userVocabularyRepository.findByUserIdAndVocabularyWordId(userId, vocabularyWordId);
            if (uv.isPresent()) {
                userVocabularyId = uv.get().getId();
                masteryScore = uv.get().getMasteryScore();
            }
        }

        return VocabularyCardSnapshot.builder()
                .id(card.getId())
                .userId(userId)
                .conversationId(conversationId)
                .vocabularyWordId(vocabularyWordId)
                .userVocabularyId(userVocabularyId)
                .word(card.getWord())
                .phonetic(card.getPhonetic())
                .partOfSpeech(card.getPartOfSpeech())
                .meaning(card.getMeaning())
                .example(card.getExample())
                .exampleTranslation(card.getExampleTranslation())
                .userMeaningGuess(card.getUserMeaningGuess())
                .meaningIsCorrect(card.getMeaningIsCorrect())
                .meaningCheckResult(card.getMeaningCheckResult())
                .chineseSentenceForTranslation(card.getChineseSentenceForTranslation())
                .meaningCheckCompleted(card.getMeaningCheckCompleted())
                .userEnglishSentence(card.getUserEnglishSentence())
                .sentenceMeaningMatches(card.getSentenceMeaningMatches())
                .sentenceHasNewWord(card.getSentenceHasNewWord())
                .sentenceAnalysisResult(card.getSentenceAnalysis())
                .sentenceAnalysisCompleted(card.getSentenceAnalysisCompleted())
                .masteryScore(masteryScore)
                .isCompleted(card.getIsCompleted())
                .build();
    }

    private List<Long> getRecentCardIds(Long userId) {
        try {
            String key = getRecentIdsKey(userId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to get recent card ids: userId={}", userId, e);
        }
        return new ArrayList<>();
    }

    private String getCardSnapshotKey(Long userId, Long cardId) {
        return REDIS_CARD_SNAPSHOT_PREFIX + userId + ":" + cardId;
    }

    private String getRecentIdsKey(Long userId) {
        return REDIS_RECENT_CARDS_IDS_PREFIX + userId;
    }
}
