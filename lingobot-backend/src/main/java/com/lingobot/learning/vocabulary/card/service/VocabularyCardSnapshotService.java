package com.lingobot.learning.vocabulary.card.service;

import com.lingobot.learning.vocabulary.card.dto.response.VocabularyCardSnapshot;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;

import java.util.List;
import java.util.Optional;

public interface VocabularyCardSnapshotService {

    void saveCardSnapshot(VocabularyCard card);

    void saveCardSnapshot(Long userId, VocabularyCard card);

    void saveCardSnapshot(VocabularyCardSnapshot snapshot);

    Optional<VocabularyCardSnapshot> getCardSnapshot(Long userId, Long cardId);

    List<VocabularyCardSnapshot> getRecentCardSnapshots(Long userId);

    List<VocabularyCardSnapshot> getRecentCardSnapshots(Long userId, int limit);

    void markCardAsRecent(Long userId, Long cardId);

    void invalidateCardSnapshot(Long userId, Long cardId);

    void invalidateAllUserSnapshots(Long userId);

    VocabularyCardSnapshot toSnapshot(Long userId, VocabularyCard card);
}
