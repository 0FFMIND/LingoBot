package com.lingobot.learning.conversation.vocabulary.service.impl;

import com.lingobot.learning.conversation.vocabulary.dto.VocabularyConversationDataDTO;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import com.lingobot.learning.conversation.vocabulary.repository.VocabularyConversationDataRepository;
import com.lingobot.learning.conversation.vocabulary.service.VocabularyConversationDataService;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyConversationDataServiceImpl implements VocabularyConversationDataService {

    private final VocabularyConversationDataRepository repository;
    private final VocabularyCardRepository vocabularyCardRepository;

    private static final int RECENT_CARDS_TO_KEEP_IN_DETAIL = 3;

    @Override
    @Transactional
    public VocabularyConversationData createOrUpdate(Long conversationId, VocabularyConversationDataDTO dto) {
        Optional<VocabularyConversationData> existing = repository.findByConversationId(conversationId);

        VocabularyConversationData data;
        if (existing.isPresent()) {
            data = existing.get();
            if (dto.getVocabularyIntent() != null) {
                data.setVocabularyIntent(dto.getVocabularyIntent());
            }
            if (dto.getVocabularyCompactedSummary() != null) {
                data.setVocabularyCompactedSummary(dto.getVocabularyCompactedSummary());
            }
            if (dto.getVocabularyCompactedCardCount() != null) {
                data.setVocabularyCompactedCardCount(dto.getVocabularyCompactedCardCount());
            }
        } else {
            data = VocabularyConversationData.builder()
                    .conversationId(conversationId)
                    .vocabularyIntent(dto.getVocabularyIntent())
                    .vocabularyCompactedSummary(dto.getVocabularyCompactedSummary())
                    .vocabularyCompactedCardCount(dto.getVocabularyCompactedCardCount() != null ? dto.getVocabularyCompactedCardCount() : 0)
                    .build();
        }

        return repository.save(data);
    }

    @Override
    public Optional<VocabularyConversationData> getByConversationId(Long conversationId) {
        return repository.findByConversationId(conversationId);
    }

    @Override
    @Transactional
    public VocabularyConversationData updateVocabularyIntent(Long conversationId, String vocabularyIntent) {
        VocabularyConversationData data = repository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());
        data.setVocabularyIntent(vocabularyIntent);
        return repository.save(data);
    }

    @Override
    @Transactional
    public VocabularyConversationData updateCompactedSummary(Long conversationId, String compactedSummary,
                                                             Long lastCompactedCardId, Integer lastCompactedPosition,
                                                             Integer compactedCardCount) {
        VocabularyConversationData data = repository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());
        data.setVocabularyCompactedSummary(compactedSummary);
        data.setVocabularyLastCompactedCardId(lastCompactedCardId);
        data.setVocabularyLastCompactedPosition(lastCompactedPosition);
        data.setVocabularyLastCompactedAt(LocalDateTime.now());
        if (compactedCardCount != null) {
            data.setVocabularyCompactedCardCount(compactedCardCount);
        }
        return repository.save(data);
    }

    @Override
    @Transactional
    public void deleteByConversationId(Long conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    @Override
    @Transactional
    public VocabularyConversationData updateLastViewedPosition(Long conversationId, Integer position) {
        VocabularyConversationData data = repository.findByConversationId(conversationId)
                .orElseGet(() -> VocabularyConversationData.builder()
                        .conversationId(conversationId)
                        .build());
        data.setLastViewedPosition(position);
        return repository.save(data);
    }

    @Override
    public String getCompactedSummary(Long conversationId) {
        return repository.findByConversationId(conversationId)
                .map(VocabularyConversationData::getVocabularyCompactedSummary)
                .orElse(null);
    }

    @Override
    public String buildVocabularyHistoryForPrompt(Long conversationId) {
        VocabularyConversationData vocabData = repository.findByConversationId(conversationId)
                .orElse(null);

        String compactedSummary = vocabData != null ? vocabData.getVocabularyCompactedSummary() : null;

        List<VocabularyCard> allCards = vocabularyCardRepository.findByConversationIdOrderByPositionAsc(conversationId);

        if (allCards == null || allCards.isEmpty()) {
            return "";
        }

        List<VocabularyCard> activeCards = allCards.stream()
                .filter(card -> !card.getIsRegenerated())
                .collect(java.util.stream.Collectors.toList());

        if (activeCards.isEmpty()) {
            return "";
        }

        List<VocabularyCard> completedCards = activeCards.stream()
                .filter(VocabularyCard::getIsCompleted)
                .collect(java.util.stream.Collectors.toList());

        List<VocabularyCard> incompleteCards = activeCards.stream()
                .filter(card -> !card.getIsCompleted())
                .collect(java.util.stream.Collectors.toList());

        StringBuilder sb = new StringBuilder();

        if (compactedSummary != null && !compactedSummary.isEmpty()) {
            sb.append("\n\n## [已Compact] 历史单词卡学习摘要\n");
            sb.append("以下是之前学习的单词摘要（已压缩以节省上下文）：\n\n");
            sb.append(compactedSummary);
            sb.append("\n\n");

            log.info("使用Compacted摘要构建词汇历史，conversationId: {}", conversationId);

            if (!completedCards.isEmpty()) {
                int recentCount = Math.min(completedCards.size(), RECENT_CARDS_TO_KEEP_IN_DETAIL);
                if (recentCount > 0) {
                    List<VocabularyCard> recentCompleted = completedCards.subList(
                            completedCards.size() - recentCount,
                            completedCards.size()
                    );

                    sb.append("## 最近完成的单词（详细信息）\n");
                    for (int i = 0; i < recentCompleted.size(); i++) {
                        VocabularyCard card = recentCompleted.get(i);
                        sb.append(i + 1).append(". **").append(card.getWord() != null ? card.getWord() : "").append("**\n");
                        if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                            sb.append("   - 释义: ").append(card.getMeaning()).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }
        } else {
            if (!completedCards.isEmpty()) {
                sb.append("\n\n## 历史单词卡学习记录\n");
                sb.append("用户之前已经学习完成了以下单词，请在生成新单词时确保不重复：\n\n");

                for (int i = 0; i < completedCards.size(); i++) {
                    VocabularyCard card = completedCards.get(i);
                    sb.append(i + 1).append(". **").append(card.getWord() != null ? card.getWord() : "").append("**\n");
                    if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                        sb.append("   - 释义: ").append(card.getMeaning()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        if (!incompleteCards.isEmpty()) {
            sb.append("\n\n## 当前学习的单词（未完成）\n");
            sb.append("⚠️ 重要提示：用户有未完成学习的单词，请优先处理这些单词，不要生成新单词！\n\n");
            sb.append("以下是用户当前正在学习但尚未完成的单词：\n\n");

            for (int i = 0; i < incompleteCards.size(); i++) {
                VocabularyCard card = incompleteCards.get(i);
                sb.append(i + 1).append(". **").append(card.getWord() != null ? card.getWord() : "").append("**\n");
                if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                    sb.append("   - 释义: ").append(card.getMeaning()).append("\n");
                }
                if (card.getCategory() != null && !card.getCategory().isEmpty()) {
                    sb.append("   - 词汇类别: ").append(card.getCategory()).append("\n");
                }
                if (card.getDifficulty() != null && !card.getDifficulty().isEmpty()) {
                    sb.append("   - 难度: ").append(card.getDifficulty()).append("\n");
                }

                if (card.getUserMeaningGuess() != null && !card.getUserMeaningGuess().isEmpty()) {
                    sb.append("   - 学习进度: 用户已猜测意思（").append(card.getUserMeaningGuess()).append("），待完成造句练习\n");
                } else {
                    sb.append("   - 学习进度: 用户还未猜测意思\n");
                }

                if (card.getUserSentence() != null && !card.getUserSentence().isEmpty()) {
                    sb.append("   - 用户造的句子: ").append(card.getUserSentence()).append("\n");
                    if (card.getSentenceAnalysisCompleted() == null || !card.getSentenceAnalysisCompleted()) {
                        sb.append("   - 状态: 句子已提交，等待 AI 分析\n");
                    } else {
                        sb.append("   - 状态: AI 已完成句子分析\n");
                    }
                }

                sb.append("\n");
            }

            sb.append("## 处理规则（重要！）\n");
            sb.append("1. 只有当用户发送`[intent:next_word]` 时，才生成新的单词卡\n");
            sb.append("2. **绝对不能**跳过未完成的单词直接生成新单词\n");
            sb.append("\n");
        }

        log.info("已为 conversationId {} 构建词汇历史信息：hasCompactedSummary={}, 已完成 {} 个，未完成 {} 个",
                conversationId,
                compactedSummary != null && !compactedSummary.isEmpty(),
                completedCards.size(),
                incompleteCards.size());

        return sb.toString();
    }
}
