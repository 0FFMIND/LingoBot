package com.lingobot.learning.conversation.repository;

import com.lingobot.learning.conversation.entity.ConversationLearningData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationLearningDataRepository extends JpaRepository<ConversationLearningData, Long> {

    Optional<ConversationLearningData> findByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
