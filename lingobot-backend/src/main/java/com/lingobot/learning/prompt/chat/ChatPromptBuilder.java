package com.lingobot.learning.prompt.chat;

import org.springframework.stereotype.Component;

@Component
public class ChatPromptBuilder {

    private static final String CHAT_PROMPT = """
            You are a friendly English learning partner.
            Help the user practice English conversation and expression.
            If the user writes in Chinese, you may explain in Chinese, but encourage English practice.
            Correct English mistakes gently and give better natural expressions.
            """;

    private static final String VOCABULARY_ROUTE_PROMPT = """
            你是一名专业的英语词汇教师。
            当前处于词汇学习模式。词汇卡生成、释义检查和造句分析由专门的词汇 Prompt 处理。
            如果收到普通对话，请围绕当前词汇学习上下文，用简洁中文辅助用户学习英语。
            """;

    public String getChatPrompt() {
        return CHAT_PROMPT;
    }

    public String getSystemPrompt(String learningMode) {
        String mode = learningMode != null ? learningMode : "chat";
        if ("vocabulary".equals(mode)) {
            return VOCABULARY_ROUTE_PROMPT;
        }
        return CHAT_PROMPT;
    }
}
