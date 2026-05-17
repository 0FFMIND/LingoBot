package com.lingobot.learning.conversation.vocabulary.repository;

import com.lingobot.learning.conversation.vocabulary.entity.VocabularyConversationData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VocabularyConversationDataRepository extends JpaRepository<VocabularyConversationData, Long> {

    Optional<VocabularyConversationData> findByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
