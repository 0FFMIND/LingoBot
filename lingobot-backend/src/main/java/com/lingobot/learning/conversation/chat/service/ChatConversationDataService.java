package com.lingobot.learning.conversation.chat.service;

import com.lingobot.learning.conversation.chat.dto.ChatConversationDataDTO;
import com.lingobot.learning.conversation.chat.entity.ChatConversationData;

import java.util.Optional;

public interface ChatConversationDataService {

    ChatConversationData createOrUpdate(Long conversationId, ChatConversationDataDTO dto);

    Optional<ChatConversationData> getByConversationId(Long conversationId);

    ChatConversationData updateCompactedSummary(Long conversationId, String compactedSummary, Integer compactedCount);

    void deleteByConversationId(Long conversationId);
}
