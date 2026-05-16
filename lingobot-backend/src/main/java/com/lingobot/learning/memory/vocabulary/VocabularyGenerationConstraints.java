package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyGenerationConstraints {
    private String category;
    private String difficulty;
    private List<String> excludeWords;
    private List<String> preferredTopics;
    private Integer maxWords;
}
