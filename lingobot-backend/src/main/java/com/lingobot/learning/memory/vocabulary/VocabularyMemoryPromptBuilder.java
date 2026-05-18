package com.lingobot.learning.memory.vocabulary;

import com.lingobot.learning.util.UserInputSanitizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class VocabularyMemoryPromptBuilder {

    public String buildPromptContext(VocabularyMemoryContext context) {
        if (context == null || context.totalMemoryItems() == 0) {
            return "";
        }

        Set<String> conversationWords = context.getConversationRecentCards().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> recentWords = context.getRecentWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> wrongWords = context.getWrongWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> regeneratedWords = context.getRegeneratedWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> masteredWords = context.getMasteredWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> reviewingWords = context.getReviewingWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> learningWords = context.getLearningWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());
        Set<String> weakWords = context.getWeakWords().stream()
                .map(VocabularyMemoryRecord::getWord)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();
        sb.append("## 词汇学习记忆\n\n");

        boolean hasCompactedSummary = context.getVocabularyCompactedSummary() != null 
                && !context.getVocabularyCompactedSummary().isBlank();
        boolean hasNewCards = !context.getConversationRecentCards().isEmpty();
        
        if (hasCompactedSummary || hasNewCards) {
            sb.append("### 当前会话历史词卡\n");
        }
        
        if (hasCompactedSummary) {
            VocabularyCompactWatermark watermark = context.getVocabularyCompactWatermark();
            sb.append("#### 历史摘要（已压缩）\n");
            if (watermark != null && watermark.getCompactedCardCount() != null) {
                sb.append("已压缩 ").append(watermark.getCompactedCardCount()).append(" 个词卡的学习记录。\n");
            }
            sb.append(context.getVocabularyCompactedSummary()).append("\n\n");
        }
        
        if (hasNewCards) {
            if (hasCompactedSummary) {
                sb.append("#### 新增学习记录\n");
            }
            sb.append("用户在当前对话中学习了以下单词，请确保不重复：\n\n");
            sb.append(formatConversationCards(context.getConversationRecentCards()));
            sb.append("\n");
        }
        
        if (!hasCompactedSummary && !hasNewCards) {
            sb.append("当前对话中没有学习过单词。\n\n");
        }

        if (!context.getMergedL1ToConversationWords().isEmpty()) {
            sb.append("### L1记忆已合并到会话\n");
            sb.append("以下L1记忆词汇已在当前会话中出现，相关记录已从短期记忆中移除：\n");
            sb.append("- ").append(String.join(", ", context.getMergedL1ToConversationWords())).append("\n\n");
        }

        sb.append("### L1 短期记忆\n");

        boolean hasL1Content = false;

        if (!context.getRecentWords().isEmpty()) {
            hasL1Content = true;
            sb.append("#### L1 最近接触词（L1_RECENT，7 天）\n");
            sb.append(formatWordListWithSource(context.getRecentWords(), "L1_RECENT",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        } else if (Boolean.TRUE.equals(context.getL1RecentEmpty())) {
            hasL1Content = true;
            String daysNote = context.getL1RecentDaysSinceLastEvent() != null
                    ? "（用户近" + context.getL1RecentDaysSinceLastEvent() + "天没有L1最近接触词记录）"
                    : "（用户近期没有L1最近接触词记录）";
            sb.append("#### L1 最近接触词（L1_RECENT，7 天）\n");
            sb.append(daysNote).append("\n\n");
        }

        if (!context.getWrongWords().isEmpty()) {
            hasL1Content = true;
            sb.append("#### L1 最近答错词（L1_WRONG，14 天，复习优先）\n");
            sb.append(formatWordListWithSource(context.getWrongWords(), "L1_WRONG",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        } else if (Boolean.TRUE.equals(context.getL1WrongEmpty())) {
            hasL1Content = true;
            String daysNote = context.getL1WrongDaysSinceLastEvent() != null
                    ? "（用户近" + context.getL1WrongDaysSinceLastEvent() + "天没有L1答错词记录）"
                    : "（用户近期没有L1答错词记录）";
            sb.append("#### L1 最近答错词（L1_WRONG，14 天，复习优先）\n");
            sb.append(daysNote).append("\n\n");
        }

        if (!context.getRegeneratedWords().isEmpty()) {
            hasL1Content = true;
            sb.append("#### L1 最近重新生成词（L1_REGENERATED，14 天，用户不满意信号）\n");
            sb.append(formatWordListWithSource(context.getRegeneratedWords(), "L1_REGENERATED",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        } else if (Boolean.TRUE.equals(context.getL1RegeneratedEmpty())) {
            hasL1Content = true;
            String daysNote = context.getL1RegeneratedDaysSinceLastEvent() != null
                    ? "（用户近" + context.getL1RegeneratedDaysSinceLastEvent() + "天没有L1重新生成词记录）"
                    : "（用户近期没有L1重新生成词记录）";
            sb.append("#### L1 最近重新生成词（L1_REGENERATED，14 天，用户不满意信号）\n");
            sb.append(daysNote).append("\n\n");
        }

        if (!hasL1Content) {
            sb.append("用户近期没有L1短期记忆记录。\n\n");
        }

        sb.append("### L2 长期记忆\n\n");

        sb.append("#### L2 State：当前长期状态\n");

        if (!context.getMasteredWords().isEmpty()) {
            sb.append("##### L2 已掌握词（MASTERED，掌握度>=80%）\n");
            sb.append(formatWordListWithSource(context.getMasteredWords(), "L2_MASTERED",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        }

        if (!context.getReviewingWords().isEmpty()) {
            sb.append("##### L2 复习中词（REVIEWING）\n");
            sb.append(formatWordListWithSource(context.getReviewingWords(), "L2_REVIEWING",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        }

        if (!context.getLearningWords().isEmpty()) {
            sb.append("##### L2 学习中词（LEARNING）\n");
            sb.append(formatWordListWithSource(context.getLearningWords(), "L2_LEARNING",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        }

        if (!context.getWeakWords().isEmpty()) {
            sb.append("##### L2 未知/薄弱词（low mastery，错误>=1个，掌握度<40%）\n");
            sb.append(formatWordListWithSource(context.getWeakWords(), "L2_WEAK",
                    conversationWords, recentWords, wrongWords, regeneratedWords,
                    masteredWords, reviewingWords, learningWords, weakWords));
            sb.append("\n");
        }

        sb.append("\n请使用这些记忆调整生成策略。");

        return sb.toString();
    }

    private String formatConversationCards(List<VocabularyMemoryRecord> cards) {
        Map<Integer, List<VocabularyMemoryRecord>> cardsByPosition = cards.stream()
                .filter(card -> card.getPosition() != null)
                .collect(Collectors.groupingBy(VocabularyMemoryRecord::getPosition));

        StringBuilder sb = new StringBuilder();
        List<Integer> positions = cardsByPosition.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (Integer position : positions) {
            List<VocabularyMemoryRecord> cardsAtPosition = cardsByPosition.get(position).stream()
                    .sorted((a, b) -> {
                        Integer idxA = a.getRegenerationIndex() != null ? a.getRegenerationIndex() : 0;
                        Integer idxB = b.getRegenerationIndex() != null ? b.getRegenerationIndex() : 0;
                        return idxA.compareTo(idxB);
                    })
                    .collect(Collectors.toList());

            sb.append("#### 位置 ").append(position + 1).append("\n");

            for (VocabularyMemoryRecord card : cardsAtPosition) {
                boolean isRegenerated = Boolean.TRUE.equals(card.getIsRegenerated());
                if (isRegenerated) {
                    sb.append("- [重新生成过的词（用户不满意）] ").append(card.getWord() != null ? card.getWord() : "");
                } else {
                    sb.append("- [当前词] ").append(card.getWord() != null ? card.getWord() : "");
                }
                sb.append("\n");
                if (card.getPartOfSpeech() != null && !card.getPartOfSpeech().isEmpty()) {
                    sb.append("  词性: ").append(card.getPartOfSpeech()).append("\n");
                }
                if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                    sb.append("  释义: ").append(card.getMeaning()).append("\n");
                }
                boolean hasMeaningCheck = card.getMeaningCheckUserAnswer() != null || card.getMeaningCheckAiFeedback() != null;
                boolean hasSentenceAnalysis = card.getSentenceAnalysisUserAnswer() != null || card.getSentenceAnalysisAiFeedback() != null;

                if (hasMeaningCheck || hasSentenceAnalysis) {
                    List<String> interactionTypes = new java.util.ArrayList<>();
                    if (hasMeaningCheck) interactionTypes.add("释义检查");
                    if (hasSentenceAnalysis) interactionTypes.add("句子分析");
                    sb.append("  交互类型: ").append(String.join(", ", interactionTypes)).append("\n");
                } else if (card.getInteractionType() != null && card.getInteractionType() != VocabularyMemoryInteractionType.NONE) {
                    String interactionLabel = card.getInteractionType() == VocabularyMemoryInteractionType.MEANING_CHECK
                            ? "释义检查"
                            : "句子分析";
                    sb.append("  交互类型: ").append(interactionLabel).append("\n");
                }

                if (hasMeaningCheck) {
                    if (card.getMeaningCheckUserAnswer() != null && !card.getMeaningCheckUserAnswer().isEmpty()) {
                        sb.append("  释义检查-用户答案: ").append(UserInputSanitizer.markAsUntrusted(card.getMeaningCheckUserAnswer())).append("\n");
                    }
                    if (card.getMeaningCheckAiFeedback() != null && !card.getMeaningCheckAiFeedback().isEmpty()) {
                        sb.append("  释义检查-AI反馈: ").append(card.getMeaningCheckAiFeedback()).append("\n");
                    }
                }

                if (hasSentenceAnalysis) {
                    if (card.getSentenceAnalysisUserAnswer() != null && !card.getSentenceAnalysisUserAnswer().isEmpty()) {
                        sb.append("  句子分析-用户答案: ").append(UserInputSanitizer.markAsUntrusted(card.getSentenceAnalysisUserAnswer())).append("\n");
                    }
                    if (card.getSentenceAnalysisAiFeedback() != null && !card.getSentenceAnalysisAiFeedback().isEmpty()) {
                        sb.append("  句子分析-AI反馈: ").append(card.getSentenceAnalysisAiFeedback()).append("\n");
                    }
                }

                if (!hasMeaningCheck && !hasSentenceAnalysis) {
                    if (card.getUserAnswer() != null && !card.getUserAnswer().isEmpty()) {
                        sb.append("  用户答案: ").append(UserInputSanitizer.markAsUntrusted(card.getUserAnswer())).append("\n");
                    }
                    if (card.getAiFeedback() != null && !card.getAiFeedback().isEmpty()) {
                        sb.append("  AI反馈: ").append(card.getAiFeedback()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatWordListWithSource(List<VocabularyMemoryRecord> words, String currentSource,
                                             Set<String> conversationWords,
                                             Set<String> recentWords,
                                             Set<String> wrongWords,
                                             Set<String> regeneratedWords,
                                             Set<String> masteredWords,
                                             Set<String> reviewingWords,
                                             Set<String> learningWords,
                                             Set<String> weakWords) {
        return words.stream()
                .map(record -> {
                    String word = record.getWord();
                    List<String> l1Sources = new java.util.ArrayList<>();
                    List<String> l2Sources = new java.util.ArrayList<>();

                    if (conversationWords.contains(word)) l1Sources.add("CONVERSATION");

                    if (recentWords.contains(word)) l1Sources.add("L1_RECENT");
                    if (wrongWords.contains(word)) l1Sources.add("L1_WRONG");
                    if (regeneratedWords.contains(word)) l1Sources.add("L1_REGENERATED");

                    if (masteredWords.contains(word)) l2Sources.add("L2_MASTERED");
                    if (reviewingWords.contains(word)) l2Sources.add("L2_REVIEWING");
                    if (learningWords.contains(word)) l2Sources.add("L2_LEARNING");
                    if (weakWords.contains(word)) l2Sources.add("L2_WEAK");

                    StringBuilder sourceTag = new StringBuilder();
                    if (!l1Sources.isEmpty() || !l2Sources.isEmpty()) {
                        sourceTag.append("\n  来源: ");
                        if (!l1Sources.isEmpty()) {
                            sourceTag.append(String.join(", ", l1Sources));
                        }
                        if (!l1Sources.isEmpty() && !l2Sources.isEmpty()) {
                            sourceTag.append("; ");
                        }
                        if (!l2Sources.isEmpty()) {
                            sourceTag.append(String.join(", ", l2Sources));
                        }
                    }

                    StringBuilder sb = new StringBuilder();
                    String eventLabel = record.getEventType() != null ? record.getEventType().name() : VocabularyMemoryEventType.UNKNOWN.name();
                    sb.append(String.format("- %s [当前: %s] (%s) - %s%s",
                            word,
                            eventLabel,
                            record.getPartOfSpeech() != null ? record.getPartOfSpeech() : "",
                            record.getMeaning() != null ? record.getMeaning() : "",
                            sourceTag.toString()));

                    if (record.getMasteryScore() != null) {
                        sb.append("\n  掌握度: ").append(record.getMasteryScore());
                    }

                    if (record.getReviewCount() > 0) {
                        sb.append("\n  复习次数: ").append(record.getReviewCount());
                    }

                    boolean hasMeaningCheck = record.getMeaningCheckUserAnswer() != null || record.getMeaningCheckAiFeedback() != null;
                    boolean hasSentenceAnalysis = record.getSentenceAnalysisUserAnswer() != null || record.getSentenceAnalysisAiFeedback() != null;

                    if (hasMeaningCheck || hasSentenceAnalysis) {
                        List<String> interactionTypes = new java.util.ArrayList<>();
                        if (hasMeaningCheck) interactionTypes.add("释义检查");
                        if (hasSentenceAnalysis) interactionTypes.add("句子分析");
                        sb.append("\n  交互类型: ").append(String.join(", ", interactionTypes));
                    } else if (record.getInteractionType() != null && record.getInteractionType() != VocabularyMemoryInteractionType.NONE) {
                        String interactionLabel = record.getInteractionType() == VocabularyMemoryInteractionType.MEANING_CHECK
                                ? "释义检查"
                                : "句子分析";
                        sb.append("\n  交互类型: ").append(interactionLabel);
                    }

                    if (hasMeaningCheck) {
                        if (record.getMeaningCheckUserAnswer() != null && !record.getMeaningCheckUserAnswer().isEmpty()) {
                            sb.append("\n  释义检查-用户答案: ").append(UserInputSanitizer.markAsUntrusted(record.getMeaningCheckUserAnswer()));
                        }
                        if (record.getMeaningCheckAiFeedback() != null && !record.getMeaningCheckAiFeedback().isEmpty()) {
                            sb.append("\n  释义检查-AI反馈: ").append(record.getMeaningCheckAiFeedback());
                        }
                    }

                    if (hasSentenceAnalysis) {
                        if (record.getSentenceAnalysisUserAnswer() != null && !record.getSentenceAnalysisUserAnswer().isEmpty()) {
                            sb.append("\n  句子分析-用户答案: ").append(UserInputSanitizer.markAsUntrusted(record.getSentenceAnalysisUserAnswer()));
                        }
                        if (record.getSentenceAnalysisAiFeedback() != null && !record.getSentenceAnalysisAiFeedback().isEmpty()) {
                            sb.append("\n  句子分析-AI反馈: ").append(record.getSentenceAnalysisAiFeedback());
                        }
                    }

                    if (!hasMeaningCheck && !hasSentenceAnalysis) {
                        if (record.getUserAnswer() != null && !record.getUserAnswer().isEmpty()) {
                            sb.append("\n  用户答案: ").append(UserInputSanitizer.markAsUntrusted(record.getUserAnswer()));
                        }
                        if (record.getAiFeedback() != null && !record.getAiFeedback().isEmpty()) {
                            sb.append("\n  AI反馈: ").append(record.getAiFeedback());
                        }
                    }

                    return sb.toString();
                })
                .collect(Collectors.joining("\n")) + "\n";
    }

}
