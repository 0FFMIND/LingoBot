package com.lingobot.conversation.repository;

import com.lingobot.conversation.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    
    List<Conversation> findAllByOrderByUpdatedAtDesc();
    
    List<Conversation> findTop10ByUserIdOrderByUpdatedAtDesc(Long userId);
    
    List<Conversation> findTop10ByOrderByUpdatedAtDesc();
    
    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
    
    Page<Conversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    
    List<Conversation> findTop20ByUserIdOrderByUpdatedAtDesc(Long userId);
    
    List<Conversation> findTop20ByOrderByUpdatedAtDesc();
    
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);
    
    boolean existsByIdAndUserId(Long id, Long userId);
}
