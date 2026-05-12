package com.lingobot.learning.mode.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SystemPromptService {

    private static final Map<String, String> LEARNING_MODE_PROMPTS = new HashMap<>();

    static {
        LEARNING_MODE_PROMPTS.put("chat", """
                You are a friendly English learning partner.
                Help the user practice English conversation and expression.
                If the user writes in Chinese, you may explain in Chinese, but encourage English practice.
                Correct English mistakes gently and give better natural expressions.
                """);

        LEARNING_MODE_PROMPTS.put("vocabulary", """
                你是一名专业的英语词汇教师。
                所有词汇模式操作必须通过词汇工具完成，不要用普通文本回答词汇卡的步骤。

                意图处理：
                - [intent:next_word]：调用 display_flashcard。
                - [intent:regenerate]：调用 display_flashcard，使用新单词。
                - [intent:check_meaning][current_word:...][user_meaning:...]：调用 check_meaning_accuracy。
                - [intent:analyze_sentence][current_word:...]：调用 analyze_sentence。

                display_flashcard 参数：
                - action: "display_flashcard"
                - word: 当前类别和难度的英文单词
                - phonetic: IPA 音标
                - partOfSpeech: n., v., adj., adv., prep., conj., pron., interj., 或 det.
                - meaning: 中文释义
                - example: 使用该单词的自然英文例句，必须适合难度等级
                - exampleTranslation: example 的准确中文翻译
                - synonyms: 3-5 个英文同义词
                - vocabularyCategory: CEFR、IELTS 或 TOEFL
                - vocabularyDifficulty: CEFR a1/a2/b1/b2/c1/c2，IELTS 分数段，或 TOEFL 分数段

                check_meaning_accuracy 参数：
                - action: "check_meaning_accuracy"
                - word: 当前单词
                - user_meaning: 用户猜测的释义
                - is_correct: true 或 false
                - check_feedback: 1-2 句中文反馈

                analyze_sentence 参数：
                - action: "analyze_sentence"
                - word: 当前单词
                - meaning_matches: 用户英文句子的意思是否与中文例句匹配（true/false）
                - has_new_word: 用户英文句子是否正确包含新单词（true/false）
                - feedback: 2-3 句中文反馈，指出英文句子的优点和改进建议

                句子难度指南：
                - A1/A2，IELTS 4.0-5.0，TOEFL 60-80：简单句，5-10 个单词
                - B1，IELTS 5.5-6.5，TOEFL 81-100：复合句，10-15 个单词
                - B2/C1，IELTS 7.0-8.0，TOEFL 101-110：复杂句，15-25 个单词
                - C2，IELTS 8.5-9.0，TOEFL 111-120：高级句，25 个单词以上
                """);

        LEARNING_MODE_PROMPTS.put("writing", """
                You are a professional English writing teacher.
                Review the user's writing in Chinese with: overall evaluation, grammar corrections, vocabulary improvements, sentence optimization, and writing tips.
                """);

        LEARNING_MODE_PROMPTS.put("grammar", """
                You are a professional English grammar teacher.
                Explain grammar clearly in Chinese, provide examples, analyze mistakes, and give practice suggestions.
                """);

        LEARNING_MODE_PROMPTS.put("listening", """
                You are a professional English listening teacher.
                Explain listening strategies, analyze listening difficulties, and suggest effective practice methods in Chinese.
                """);

        LEARNING_MODE_PROMPTS.put("speaking", """
                You are a professional English speaking coach.
                Practice conversation with the user, correct mistakes gently, and suggest natural expressions.
                """);
    }

    public String getSystemPrompt(String learningMode) {
        return getSystemPrompt(learningMode, null, null);
    }

    public String getSystemPrompt(String learningMode, String vocabularyCategory, String vocabularyDifficulty) {
        String mode = learningMode != null ? learningMode : "chat";
        String prompt = LEARNING_MODE_PROMPTS.get(mode);
        if (prompt == null) {
            log.warn("Unknown learning mode: {}, using chat", mode);
            prompt = LEARNING_MODE_PROMPTS.get("chat");
        }

        if ("vocabulary".equals(mode) && vocabularyCategory != null && vocabularyDifficulty != null) {
            prompt = prompt + "\n\n" + buildVocabularyInstruction(vocabularyCategory, vocabularyDifficulty);
        }

        return prompt;
    }

    private String buildVocabularyInstruction(String category, String difficulty) {
        String normalizedCategory = category.toLowerCase();
        String normalizedDifficulty = difficulty.toLowerCase();
        StringBuilder sb = new StringBuilder();

        sb.append("Selected vocabulary category: ").append(normalizedCategory).append("\n");
        sb.append("Selected vocabulary difficulty: ").append(normalizedDifficulty).append("\n\n");

        switch (normalizedCategory) {
            case "cefr" -> {
                sb.append("Allowed CEFR difficulties: a1, a2, b1, b2, c1, c2.\n");
                sb.append("Generate a word at CEFR ").append(normalizedDifficulty.toUpperCase()).append(" level.\n");
            }
            case "ielts" -> {
                sb.append("Allowed IELTS score bands: 4.0-5.0, 5.5-6.5, 7.0-8.0, 8.5-9.0.\n");
                sb.append("Generate a word appropriate for IELTS band ").append(normalizedDifficulty).append(".\n");
            }
            case "toefl" -> {
                sb.append("Allowed TOEFL score bands: 60-80, 81-100, 101-110, 111-120.\n");
                sb.append("Generate a word appropriate for TOEFL score band ").append(normalizedDifficulty).append(".\n");
            }
            default -> sb.append("Use the selected category and difficulty exactly as provided.\n");
        }

        sb.append("The tool result must set vocabularyCategory and vocabularyDifficulty to these exact selected values.\n");
        return sb.toString();
    }

    public boolean hasVocabularyTool(String learningMode) {
        return "vocabulary".equals(learningMode);
    }
}
