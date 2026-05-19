package com.lingobot.learning.chat.controller;

import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.memory.vocabulary.VocabularyCompactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextManagerService contextManagerService;
    private final ConversationService conversationService;
    private final VocabularyCompactService vocabularyCompactService;

    @GetMapping("/status/{publicId}")
    public ResponseEntity<ApiResponse<Object>> getContextStatus(@PathVariable String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        return ResponseEntity.ok(ApiResponse.success(contextManagerService.getContextStatus(conversationId)));
    }

    @PostMapping("/compact/{publicId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compact(@PathVariable String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        Map<String, Object> result = vocabularyCompactService.executeCompact(conversationId);

        Boolean executed = (Boolean) result.get("executed");
        if (Boolean.TRUE.equals(executed)) {
            return ResponseEntity.ok(ApiResponse.success("压缩成功", result));
        } else {
            String reason = result.get("reason") != null ? result.get("reason").toString() : "压缩未执行";
            return ResponseEntity.ok(ApiResponse.success(reason, result));
        }
    }
}
