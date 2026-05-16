package com.lingobot.learning.memory.vocabulary;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class VocabularyMemoryPromptBuilder {

    public String buildPromptContext(VocabularyMemoryContext context) {
        if (context == null || context.totalMemoryItems() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Vocabulary Learning Memory\n\n");

        if (!context.getConversationRecentCards().isEmpty()) {
            sb.append("### Current Conversation Recent Cards\n");
            sb.append(formatWordList(context.getConversationRecentCards()));
            sb.append("\n");
        }

        if (!context.getRecentWords().isEmpty()) {
            sb.append("### L1 Recent Contacted Words (7 days)\n");
            sb.append(formatWordList(context.getRecentWords()));
            sb.append("\n");
        }

        if (!context.getWrongWords().isEmpty()) {
            sb.append("### L1 Wrong Words (14 days, review priority)\n");
            sb.append(formatWordList(context.getWrongWords()));
            sb.append("\n");
        }

        if (!context.getRegeneratedWords().isEmpty()) {
            sb.append("### L1 Regenerated Words (14 days, dissatisfaction signal)\n");
            sb.append(formatWordList(context.getRegeneratedWords()));
            sb.append("\n");
        }

        if (!context.getMasteredWords().isEmpty()) {
            sb.append("### Mastered Words\n");
            sb.append(formatWordList(context.getMasteredWords()));
            sb.append("\n");
        }

        if (!context.getWeakWords().isEmpty()) {
            sb.append("### Words Needing Review\n");
            sb.append(formatWordList(context.getWeakWords()));
            sb.append("\n");
        }

        if (!context.getExcludedWords().isEmpty()) {
            sb.append("### Words to Exclude (already used recently)\n");
            sb.append("- ").append(String.join(", ", context.getExcludedWords()));
            sb.append("\n");
        }

        sb.append("\nPlease use this memory to avoid repeating words and adapt to the user's learning progress.");

        return sb.toString();
    }

    private String formatWordList(List<VocabularyMemoryRecord> words) {
        return words.stream()
                .map(record -> String.format("- %s [%s] (%s) - %s",
                        record.getWord(),
                        record.getEventType() != null ? record.getEventType() : VocabularyMemoryEventType.UNKNOWN,
                        record.getPartOfSpeech() != null ? record.getPartOfSpeech() : "",
                        record.getMeaning() != null ? record.getMeaning() : ""))
                .collect(Collectors.joining("\n")) + "\n";
    }
}
