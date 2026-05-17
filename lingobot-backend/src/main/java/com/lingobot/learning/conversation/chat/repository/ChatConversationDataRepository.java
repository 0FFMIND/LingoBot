package com.lingobot.learning.conversation.chat.repository;

import com.lingobot.learning.conversation.chat.entity.ChatConversationData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatConversationDataRepository extends JpaRepository<ChatConversationData, Long> {

    Optional<ChatConversationData> findByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
