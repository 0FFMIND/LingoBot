package com.lingobot.core.conversation.repository;

import com.lingobot.core.conversation.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByTimestampAsc(Long conversationId);

    List<Message> findByConversationIdOrderByTimestampDesc(Long conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.timestamp DESC")
    List<Message> findLastMessagesByConversationId(@Param("conversationId") Long conversationId);

    int countByConversationId(Long conversationId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.id = :id")
    void deleteMessageById(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.id IN :ids")
    void deleteMessagesByIds(@Param("ids") List<Long> ids);
}
