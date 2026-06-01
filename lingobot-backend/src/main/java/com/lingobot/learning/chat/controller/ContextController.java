package com.lingobot.learning.chat.controller;

import com.lingobot.core.conversation.service.ConversationService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.learning.chat.service.ContextManagerService;
import com.lingobot.learning.chat.service.MemoryCompactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextManagerService contextManagerService;
    private final ConversationService conversationService;
    private final MemoryCompactService memoryCompactService;

    @GetMapping("/status/{publicId}")
    public ResponseEntity<ApiResponse<Object>> getContextStatus(@PathVariable String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        return ResponseEntity.ok(ApiResponse.success(contextManagerService.getContextStatus(conversationId)));
    }

    @PostMapping("/compact/{publicId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compact(@PathVariable String publicId) {
        Long conversationId = conversationService.resolvePublicIdToId(publicId);
        MemoryCompactService.CompactResult compactResult = memoryCompactService.executeCompact(conversationId);

        Map<String, Object> result = new HashMap<>();
        result.put("executed", compactResult.isExecuted());
        result.put("reason", compactResult.getReason());
        result.put("beforeTokens", compactResult.getBeforeTokens());
        result.put("afterTokens", compactResult.getAfterTokens());
        result.put("savedTokens", compactResult.getSavedTokens());
        result.put("compactedCardCount", compactResult.getCompactedCardCount());
        result.put("compactedCardsCount", compactResult.getCompactedCardsCount());
        result.put("recentCardsCount", compactResult.getRecentCardsCount());
        result.put("totalCompactedCards", compactResult.getTotalCompactedCards());
        result.put("compactBatch", compactResult.getCompactBatch());

        if (compactResult.isExecuted()) {
            return ResponseEntity.ok(ApiResponse.success("压缩成功", result));
        } else {
            return ResponseEntity.ok(ApiResponse.success(compactResult.getReason(), result));
        }
    }
}
