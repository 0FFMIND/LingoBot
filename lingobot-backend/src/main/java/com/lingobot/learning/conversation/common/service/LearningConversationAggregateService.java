package com.lingobot.learning.conversation.common.service;

import com.lingobot.core.conversation.dto.ConversationDTO;
import com.lingobot.learning.conversation.chat.entity.ChatConversationData;
import com.lingobot.learning.conversation.common.dto.ContextStatusDTO;
import com.lingobot.learning.conversation.common.dto.LearningConversationDTO;
import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;

import java.util.Optional;

public interface LearningConversationAggregateService {

    LearningConversationDTO enrich(ConversationDTO conversation, Long conversationId);

    String resolveLearningMode(Optional<ChatConversationData> chatData,
                                Optional<VocabularyConversationData> vocabData);

    ContextStatusDTO getContextStatus(Long conversationId);
}
