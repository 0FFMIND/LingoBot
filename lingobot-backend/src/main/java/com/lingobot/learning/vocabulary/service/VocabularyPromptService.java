package com.lingobot.learning.vocabulary.service;

import org.springframework.stereotype.Service;

@Service
public class VocabularyPromptService {

    public String getDisplayFlashcardPrompt(String vocabularyCategory, String vocabularyDifficulty) {
        StringBuilder prompt = new StringBuilder("""
                你是一名专业的英语词汇教师。
                当前任务：生成一张新的英文单词卡。
                你必须调用 vocabulary 工具，并且只调用 display_flashcard。
                不要用普通文本回答。

                单词卡要求：
                - word: 符合当前类别和难度的英文单词
                - phonetic: IPA 音标
                - partOfSpeech: n., v., adj., adv., prep., conj., pron., interj. 或 det.
                - meaning: 准确、简洁的中文释义
                - example: 使用该单词的自然英文例句，难度必须匹配
                - exampleTranslation: example 的准确中文翻译
                - synonyms: 3-5 个英文同义词
                - vocabularyCategory 和 vocabularyDifficulty 必须与用户选择一致

                句子难度指南：
                - A1/A2，IELTS 4.0-5.0，TOEFL 60-80：简单句，5-10 个单词
                - B1，IELTS 5.5-6.5，TOEFL 81-100：复合句，10-15 个单词
                - B2/C1，IELTS 7.0-8.0，TOEFL 101-110：复杂句，15-25 个单词
                - C2，IELTS 8.5-9.0，TOEFL 111-120：高级句，25 个单词以上
                """);

        if (vocabularyCategory != null && vocabularyDifficulty != null) {
            prompt.append("\n").append(buildVocabularyInstruction(vocabularyCategory, vocabularyDifficulty));
        }
        return prompt.toString();
    }

    public String getMeaningCheckPrompt(
            String word,
            String phonetic,
            String partOfSpeech,
            String correctMeaning,
            String example,
            String exampleTranslation,
            String userMeaning) {
        return """
                你是一名严谨的英语词汇教师。
                当前任务：检查用户给出的中文释义是否准确。
                你必须调用 vocabulary 工具，并且只调用 check_meaning_accuracy。
                不要用普通文本回答。

                当前正在学习的单词卡：
                - word: %s
                - phonetic: %s
                - partOfSpeech: %s
                - correct meaning: %s
                - example: %s
                - exampleTranslation: %s

                用户本次输入的中文释义：
                %s

                判断标准：
                - 如果用户释义覆盖了核心含义，即使表达不完全一致，也可以判为正确。
                - 如果用户只写了无关内容、过于宽泛、过于狭窄或明显错误，判为错误。
                - check_feedback 用中文写 1-2 句，先给结论，再说明正确含义。

                工具参数要求：
                - action: "check_meaning_accuracy"
                - word: 当前单词
                - user_meaning: 用户输入的中文释义
                - is_correct: true 或 false
                - check_feedback: 中文反馈
                """.formatted(
                textOrUnknown(word),
                textOrUnknown(phonetic),
                textOrUnknown(partOfSpeech),
                textOrUnknown(correctMeaning),
                textOrUnknown(example),
                textOrUnknown(exampleTranslation),
                textOrUnknown(userMeaning));
    }

    public String getSentenceAnalysisPrompt(
            String word,
            String phonetic,
            String partOfSpeech,
            String meaning,
            String chineseSentence,
            String userEnglishSentence) {
        return """
                你是一名专业的英语写作与词汇教师。
                当前任务：分析用户根据中文例句写出的英文句子。
                你必须调用 vocabulary 工具，并且只调用 analyze_sentence。
                不要用普通文本回答。

                当前正在学习的单词卡：
                - word: %s
                - phonetic: %s
                - partOfSpeech: %s
                - meaning: %s

                中文例句：
                %s

                用户本次写出的英文句子：
                %s

                判断标准：
                - meaning_matches: 英文句子是否表达了中文例句的核心意思。
                - has_new_word: 英文句子是否自然、正确地包含当前新单词。
                - feedback 用中文写 2-3 句，指出优点、错误和可改进表达。

                工具参数要求：
                - action: "analyze_sentence"
                - word: 当前单词
                - meaning_matches: true 或 false
                - has_new_word: true 或 false
                - feedback: 中文反馈
                """.formatted(
                textOrUnknown(word),
                textOrUnknown(phonetic),
                textOrUnknown(partOfSpeech),
                textOrUnknown(meaning),
                textOrUnknown(chineseSentence),
                textOrUnknown(userEnglishSentence));
    }

    private String buildVocabularyInstruction(String category, String difficulty) {
        String normalizedCategory = category.toLowerCase();
        String normalizedDifficulty = difficulty.toLowerCase();
        StringBuilder sb = new StringBuilder();

        sb.append("已选择的词汇类别：").append(normalizedCategory).append("\n");
        sb.append("已选择的词汇难度：").append(normalizedDifficulty).append("\n\n");

        switch (normalizedCategory) {
            case "cefr" -> {
                sb.append("允许的 CEFR 等级：a1, a2, b1, b2, c1, c2。\n");
                sb.append("请生成一个适合 CEFR ").append(normalizedDifficulty.toUpperCase()).append(" 等级的单词。\n");
            }
            case "ielts" -> {
                sb.append("允许的 IELTS 分数段：4.0-5.0, 5.5-6.5, 7.0-8.0, 8.5-9.0。\n");
                sb.append("请生成一个适合 IELTS ").append(normalizedDifficulty).append(" 分数段的单词。\n");
            }
            case "toefl" -> {
                sb.append("允许的 TOEFL 分数段：60-80, 81-100, 101-110, 111-120。\n");
                sb.append("请生成一个适合 TOEFL ").append(normalizedDifficulty).append(" 分数段的单词。\n");
            }
            default -> sb.append("请严格使用用户选择的类别和难度。\n");
        }

        sb.append("工具返回结果中的 vocabularyCategory 和 vocabularyDifficulty 必须设置为这些精确的用户选择值。\n");
        return sb.toString();
    }

    private String textOrUnknown(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }
}
