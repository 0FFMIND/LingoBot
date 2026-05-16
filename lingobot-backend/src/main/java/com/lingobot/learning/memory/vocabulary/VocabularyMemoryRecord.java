package com.lingobot.learning.memory.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyMemoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;
    private String word;
    private String meaning;
    private String partOfSpeech;
    private BigDecimal masteryScore;
    private LocalDateTime lastReviewedAt;
    private int reviewCount;
    private boolean isMastered;
    private VocabularyMemoryEventType eventType;
    private LocalDateTime eventTimestamp;
    private Integer position;
    private Integer regenerationIndex;
    private Boolean isRegenerated;
    private String userAnswer;
    private String aiFeedback;
    private VocabularyMemoryInteractionType interactionType;
    private String meaningCheckUserAnswer;
    private String meaningCheckAiFeedback;
    private Boolean meaningCheckIsCorrect;
    private String sentenceAnalysisUserAnswer;
    private String sentenceAnalysisAiFeedback;
    private Boolean sentenceAnalysisIsCorrect;
    @Builder.Default
    private List<VocabularyMemoryTier> sourceTiers = new ArrayList<>();
    @Builder.Default
    private List<String> learningEvents = new ArrayList<>();
}
