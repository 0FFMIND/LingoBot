package com.lingobot.learning.prompt.vocabulary;

public final class VocabularyPreferencePromptBuilder {

    private VocabularyPreferencePromptBuilder() {}

    public static String buildVocabularyInstruction(String category, String difficulty) {
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
}
