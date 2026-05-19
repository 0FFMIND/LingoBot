package com.lingobot.learning.vocabulary.graph;

import com.lingobot.core.conversation.dto.TokenUsageDTO;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationConstraints;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryContext;
import com.lingobot.learning.vocabulary.card.dto.response.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.card.dto.response.WordCardData;
import org.bsc.langgraph4j.state.AgentState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单词卡片生成状态对象。
 *
 * 继承自 AgentState，作为 LangGraph 工作流的状态载体，
 * 在各个节点（MEMORY_RECALL、PLANNING、GENERATION、VALIDATION、PERSISTENCE）
 * 之间传递数据，包括输入参数、中间结果、生成的卡片数据、校验结果等。
 *
 * 每个 getter 方法从状态 Map 中安全地读取对应字段，
 * 类型转换失败或字段不存在时返回默认值（null 或空集合）。
 */
public class VocabularyState extends AgentState {

    public VocabularyState(Map<String, Object> initData) {
        super(initData);
    }

    // 获取对话ID
    public Long getConversationId() {
        return value("conversationId").map(Long.class::cast).orElse(null);
    }

    // 获取用户ID
    public Long getUserId() {
        return value("userId").map(Long.class::cast).orElse(null);
    }

    // 获取词汇分类（如 cefr、ielts 等）
    public String getCategory() {
        return value("category").map(String.class::cast).orElse(null);
    }

    // 获取难度级别（如 a1、b2、c1 等）
    public String getDifficulty() {
        return value("difficulty").map(String.class::cast).orElse(null);
    }

    // 获取生成意图（新建、复习、重新生成等）
    public VocabularyGenerationIntent getIntent() {
        return value("intent").map(VocabularyGenerationIntent.class::cast).orElse(null);
    }

    // 获取使用的AI模型名称
    public String getModel() {
        return value("model").map(String.class::cast).orElse(null);
    }

    // 获取记忆上下文（用户历史学习记录）
    public VocabularyMemoryContext getMemoryContext() {
        return value("memoryContext").map(VocabularyMemoryContext.class::cast).orElse(null);
    }

    // 获取排除词列表（避免重复生成已学过的单词）
    @SuppressWarnings("unchecked")
    public List<String> getExcludedWords() {
        return value("excludedWords")
                .map(obj -> (List<String>) obj)
                .orElse(new ArrayList<>());
    }

    // 获取生成约束条件（分类、难度、排除词等）
    public VocabularyGenerationConstraints getConstraints() {
        return value("constraints").map(VocabularyGenerationConstraints.class::cast).orElse(null);
    }

    // 获取系统提示词
    public String getSystemPrompt() {
        return value("systemPrompt").map(String.class::cast).orElse(null);
    }

    // 获取AI生成的单词卡片数据
    public WordCardData getGeneratedCard() {
        return value("generatedCard").map(WordCardData.class::cast).orElse(null);
    }

    // 获取Token使用统计
    public TokenUsageDTO getTokenUsage() {
        return value("tokenUsage").map(TokenUsageDTO.class::cast).orElse(null);
    }

    // 获取校验错误列表
    @SuppressWarnings("unchecked")
    public List<String> getValidationErrors() {
        return value("validationErrors")
                .map(obj -> (List<String>) obj)
                .orElse(new ArrayList<>());
    }

    // 校验是否通过
    public boolean isValid() {
        return value("isValid").map(Boolean.class::cast).orElse(false);
    }

    // 获取已持久化到数据库的卡片
    public VocabularyCardDTO getSavedCard() {
        return value("savedCard").map(VocabularyCardDTO.class::cast).orElse(null);
    }

    // 获取重试次数
    public int getRetryCount() {
        return value("retryCount").map(Integer.class::cast).orElse(0);
    }

    // 获取重新生成的位置（REGENERATE意图时使用）
    public Integer getRegeneratePosition() {
        return value("regeneratePosition").map(Integer.class::cast).orElse(null);
    }

    // 获取重新生成时的旧单词
    public String getRegenerateOldWord() {
        return value("regenerateOldWord").map(String.class::cast).orElse(null);
    }

    // 获取重新生成时的旧词性
    public String getRegenerateOldPartOfSpeech() {
        return value("regenerateOldPartOfSpeech").map(String.class::cast).orElse(null);
    }

    // 获取重新生成时的旧释义
    public String getRegenerateOldMeaning() {
        return value("regenerateOldMeaning").map(String.class::cast).orElse(null);
    }
}
