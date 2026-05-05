package com.lingobot.learning.vocabulary.service;

import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.mode.service.SystemPromptService;
import com.lingobot.learning.llm.tool.service.McpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeaningCheckService {

    private final ToolLoopService toolLoopService;
    private final McpService mcpService;
    private final SystemPromptService systemPromptService;
    private final VocabularyStateService vocabularyStateService;

    private static final String DEFAULT_MODEL = "qwen";

    @Async("meaningCheckExecutor")
    public void checkUserMeaningAsync(Long conversationId, Long cardId, String userMeaning) {
        try {
            log.info("Starting async meaning check for cardId={}, conversationId={}", cardId, conversationId);

            Map<String, Object> cachedState = vocabularyStateService.getCurrentWord(conversationId);
            String word = cachedState != null ? (String) cachedState.get("word") : null;
            String correctMeaning = cachedState != null ? (String) cachedState.get("meaning") : null;

            if (word == null || word.isEmpty()) {
                log.warn("No cached word for conversationId={}, skipping meaning check", conversationId);
                return;
            }

            String systemPrompt = systemPromptService.getSystemPrompt("vocabulary");

            List<OpenAiChatMessage> messages = new ArrayList<>();
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));

            String userMessage = String.format(
                "[intent:check_meaning][current_word:%s][user_meaning:%s]\n正确释义�?s",
                word, userMeaning, correctMeaning != null ? correctMeaning : "未知"
            );
            messages.add(OpenAiChatMessage.createTextMessage("user", userMessage));

            List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary");
            if (tools == null || tools.isEmpty()) {
                log.warn("No vocabulary tools available for meaning check");
                return;
            }

            toolLoopService.executeOneTimeToolCall(conversationId, messages, tools, DEFAULT_MODEL);
            log.info("Meaning check completed for cardId={}", cardId);
        } catch (Exception e) {
            log.error("Meaning check failed for cardId={}, conversationId={}", cardId, conversationId, e);
        }
    }
}
