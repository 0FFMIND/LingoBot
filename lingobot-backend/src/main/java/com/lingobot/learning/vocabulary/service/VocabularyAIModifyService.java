package com.lingobot.learning.vocabulary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.infrastructure.common.config.LlmProperties;
import com.lingobot.infrastructure.common.exception.BusinessException;
import com.lingobot.infrastructure.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.infrastructure.llm.service.LlmService;
import com.lingobot.learning.vocabulary.dto.AIModifyVocabularyRequest;
import com.lingobot.learning.vocabulary.dto.UpdateUserVocabularyRequest;
import com.lingobot.learning.vocabulary.dto.UserVocabularyDTO;
import com.lingobot.learning.vocabulary.entity.UserVocabulary;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyAIModifyService {

    private final LlmService llmService;
    private final LlmProperties llmProperties;
    private final UserVocabularyRepository userVocabularyRepository;
    private final UserVocabularyService userVocabularyService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = "You are a professional English vocabulary editor. Return JSON only.";

private static final String USER_PROMPT_TEMPLATE = """
        请检查这张词汇卡，并填写或完善所有可编辑的显示字段：
        当前卡片：
        - 单词: %s
        - 音标: %s
        - 词性: %s
        - 中文释义: %s
        - 类别: %s
        - 难度: %s

        可选类别：
        - CEFR
        - IELTS
        - TOEFL

        可选难度：
        - CEFR: A1, A2, B1, B2, C1, C2
        - IELTS: 4.0-5.0, 5.5-6.5, 7.0-8.0, 8.5-9.0
        - TOEFL: 60-80, 81-100, 101-110, 111-120

        可选词性：
        n., v., adj., adv., prep., conj., pron., interj., det.

        规则：
        1. 不要修改单词；
        2. 如果某个字段缺失，请填写完整；
        3. 验证类别和难度准确无误：
           - 如果类别和难度不匹配，优先根据已有类别选择最合理的难度；
           - 如果类别缺失但已有难度，则根据难度对应的类别进行填写，然后验证难度是否正确，若不正确则修改为更符合的难度；
           - 如果类别和难度都缺失，请根据单词本身的难度推测类别，再选择合理难度；
        4. 返回所有字段，不仅仅是修改过的字段；
        5. 中文释义必须使用中文。

        返回 JSON 格式如下：
        {
          "phonetic": "...",
          "partOfSpeech": "...",
          "meaning": "...",
          "synonyms": ["...", "..."],
          "category": "...",
          "difficulty": "..."
        }
        """;

    // 调用 LLM 完善词汇信息：读取当前字段、构建提示词、解析 AI 返回、更新词汇记录
    @Transactional
    public UserVocabularyDTO modifyWithAI(AIModifyVocabularyRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("User is not logged in");
        }

        UserVocabulary vocabulary = userVocabularyRepository.findById(request.getId())
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Vocabulary record not found or not accessible"));

        String word = firstNonBlank(request.getWord(), vocabulary.getWord());
        String phonetic = firstNonBlank(request.getPhonetic(), vocabulary.getPhonetic());
        String partOfSpeech = firstNonBlank(request.getPartOfSpeech(), vocabulary.getPartOfSpeech());
        String meaning = firstNonBlank(request.getMeaning(), vocabulary.getMeaning());
        String category = firstNonBlank(request.getCategory(), vocabulary.getCategory());
        String difficulty = firstNonBlank(request.getDifficulty(), vocabulary.getDifficulty());

        String userPrompt = String.format(
                USER_PROMPT_TEMPLATE,
                word,
                phonetic,
                partOfSpeech,
                meaning,
                category,
                difficulty
        );

        log.info("Calling AI vocabulary modify: id={}, word={}", request.getId(), word);

        List<OpenAiChatMessage> messages = new ArrayList<>();
        messages.add(OpenAiChatMessage.createTextMessage("system", SYSTEM_PROMPT));
        messages.add(OpenAiChatMessage.createTextMessage("user", userPrompt));

        String aiResponse;
        try {
            aiResponse = llmService.chat(llmProperties.getModel(), messages);
            log.debug("AI vocabulary modify raw response: {}", aiResponse);
        } catch (Exception e) {
            log.error("AI vocabulary modify call failed", e);
            throw BusinessException.badRequest("AI modify failed: " + e.getMessage());
        }

        UpdateUserVocabularyRequest updateRequest = parseAIResponse(aiResponse, request, vocabulary);
        return userVocabularyService.updateVocabulary(userId, request.getId(), updateRequest);
    }

    // 解析 AI 返回的 JSON，用有效字段覆盖 updateRequest，无效字段保留原值
    private UpdateUserVocabularyRequest parseAIResponse(
            String aiResponse,
            AIModifyVocabularyRequest original,
            UserVocabulary existing) {
        UpdateUserVocabularyRequest updateRequest = new UpdateUserVocabularyRequest();

        updateRequest.setWord(firstNonBlank(original.getWord(), existing.getWord()));
        updateRequest.setPhonetic(firstNonBlank(original.getPhonetic(), existing.getPhonetic()));
        updateRequest.setPartOfSpeech(firstNonBlank(original.getPartOfSpeech(), existing.getPartOfSpeech()));
        updateRequest.setMeaning(firstNonBlank(original.getMeaning(), existing.getMeaning()));
        updateRequest.setSynonyms(original.getSynonyms() != null ? original.getSynonyms() : existing.getSynonyms());
        updateRequest.setCategory(firstNonBlank(original.getCategory(), existing.getCategory()));
        updateRequest.setDifficulty(firstNonBlank(original.getDifficulty(), existing.getDifficulty()));

        try {
            String jsonStr = extractJson(aiResponse);
            if (jsonStr == null) {
                log.warn("Could not extract JSON from AI response; keeping current values");
                return updateRequest;
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            applyText(root, "phonetic", updateRequest::setPhonetic);
            applyValidatedText(root, "partOfSpeech", updateRequest::setPartOfSpeech, this::isValidPartOfSpeech);
            applyText(root, "meaning", updateRequest::setMeaning);
            applySynonyms(root, updateRequest);

            if (root.has("category") && !root.get("category").isNull()) {
                String value = root.get("category").asText().trim().toLowerCase();
                if (isValidCategory(value)) {
                    updateRequest.setCategory(value);
                    if (!isDifficultyValidForCategory(updateRequest.getDifficulty(), value)) {
                        updateRequest.setDifficulty(getDefaultDifficultyForCategory(value));
                    }
                }
            }

            if (root.has("difficulty") && !root.get("difficulty").isNull()) {
                String value = root.get("difficulty").asText().trim().toLowerCase();
                String category = updateRequest.getCategory();
                if (isDifficultyValidForCategory(value, category)) {
                    updateRequest.setDifficulty(value);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse AI vocabulary modify response; keeping current values", e);
        }

        return updateRequest;
    }

    // 从 JSON 节点中的非空字符串字段写入 setter
    private void applyText(JsonNode root, String field, java.util.function.Consumer<String> setter) {
        if (root.has(field) && !root.get(field).isNull()) {
            String value = root.get(field).asText().trim();
            if (!value.isEmpty()) {
                setter.accept(value);
            }
        }
    }

    // 从 JSON 节点中通过校验的字符串字段写入 setter，校验不通过则保留原值
    private void applyValidatedText(
            JsonNode root,
            String field,
            java.util.function.Consumer<String> setter,
            java.util.function.Predicate<String> validator) {
        if (root.has(field) && !root.get(field).isNull()) {
            String value = root.get(field).asText().trim();
            if (validator.test(value)) {
                setter.accept(value);
            }
        }
    }

    // 从 JSON 节点解析同义词数组并写入 updateRequest
    private void applySynonyms(JsonNode root, UpdateUserVocabularyRequest updateRequest) {
        if (!root.has("synonyms") || !root.get("synonyms").isArray()) {
            return;
        }
        List<String> synonyms = new ArrayList<>();
        for (JsonNode item : root.get("synonyms")) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                synonyms.add(value);
            }
        }
        updateRequest.setSynonyms(synonyms);
    }

    // 从文本中提取第一个完整的 JSON 对象（按括号深度匹配）
    private String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        int end = -1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                if (--depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        return end < 0 ? null : text.substring(start, end + 1);
    }

    // 校验词性是否在允许的枚举值范围内（n./v./adj. 等）
    private boolean isValidPartOfSpeech(String pos) {
        if (pos == null) return false;
        String[] valid = {"n.", "v.", "adj.", "adv.", "prep.", "conj.", "pron.", "interj.", "det."};
        for (String validPos : valid) {
            if (validPos.equals(pos)) return true;
        }
        return false;
    }

    // 校验词汇类别是否合法（cefr/ielts/toefl）
    private boolean isValidCategory(String category) {
        return "cefr".equals(category) || "ielts".equals(category) || "toefl".equals(category);
    }

    // 校验难度级别是否与词汇类别匹配（不同类别有各自的难度枚举）
    private boolean isDifficultyValidForCategory(String difficulty, String category) {
        if (difficulty == null || category == null) return false;
        return switch (category) {
            case "cefr" -> oneOf(difficulty, "a1", "a2", "b1", "b2", "c1", "c2");
            case "ielts" -> oneOf(difficulty, "4.0-5.0", "5.5-6.5", "7.0-8.0", "8.5-9.0");
            case "toefl" -> oneOf(difficulty, "60-80", "81-100", "101-110", "111-120");
            default -> false;
        };
    }

    // 获取各词汇类别的默认难度值（类别与难度不匹配时的兜底）
    private String getDefaultDifficultyForCategory(String category) {
        return switch (category) {
            case "cefr" -> "b2";
            case "ielts" -> "5.5-6.5";
            case "toefl" -> "81-100";
            default -> "";
        };
    }

    private boolean oneOf(String value, String... allowed) {
        for (String item : allowed) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    // 优先返回非空的 preferred，否则返回 fallback（null 时返回空字符串）
    private String firstNonBlank(String preferred, String fallback) {
        return isNotBlank(preferred) ? preferred.trim() : (fallback != null ? fallback : "");
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
