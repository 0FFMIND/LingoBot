package com.lingobot.learning.conversation.chat.service.impl;

import com.lingobot.learning.conversation.chat.dto.ChatConversationDataDTO;
import com.lingobot.learning.conversation.chat.entity.ChatConversationData;
import com.lingobot.learning.conversation.chat.repository.ChatConversationDataRepository;
import com.lingobot.learning.conversation.chat.service.ChatConversationDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatConversationDataServiceImpl implements ChatConversationDataService {

    private final ChatConversationDataRepository repository;

    @Override
    @Transactional
    public ChatConversationData createOrUpdate(Long conversationId, ChatConversationDataDTO dto) {
        Optional<ChatConversationData> existing = repository.findByConversationId(conversationId);

        ChatConversationData data;
        if (existing.isPresent()) {
            data = existing.get();
            if (dto.getCompactedSummary() != null) {
                data.setCompactedSummary(dto.getCompactedSummary());
            }
            if (dto.getCompactedCount() != null) {
                data.setCompactedCount(dto.getCompactedCount());
            }
            if (dto.getTotalTokensEstimate() != null) {
                data.setTotalTokensEstimate(dto.getTotalTokensEstimate());
            }
        } else {
            data = ChatConversationData.builder()
                    .conversationId(conversationId)
                    .compactedSummary(dto.getCompactedSummary())
                    .compactedCount(dto.getCompactedCount() != null ? dto.getCompactedCount() : 0)
                    .totalTokensEstimate(dto.getTotalTokensEstimate() != null ? dto.getTotalTokensEstimate() : 0L)
                    .build();
        }

        return repository.save(data);
    }

    @Override
    public Optional<ChatConversationData> getByConversationId(Long conversationId) {
        return repository.findByConversationId(conversationId);
    }

    @Override
    @Transactional
    public ChatConversationData updateCompactedSummary(Long conversationId, String compactedSummary, Integer compactedCount) {
        ChatConversationData data = repository.findByConversationId(conversationId)
                .orElseGet(() -> ChatConversationData.builder()
                        .conversationId(conversationId)
                        .build());
        data.setCompactedSummary(compactedSummary);
        data.setCompactedCount(compactedCount);
        data.setLastCompactedAt(LocalDateTime.now());
        return repository.save(data);
    }

    @Override
    @Transactional
    public void deleteByConversationId(Long conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
