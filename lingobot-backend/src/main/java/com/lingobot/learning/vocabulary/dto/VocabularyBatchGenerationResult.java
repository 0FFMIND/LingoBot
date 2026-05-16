package com.lingobot.learning.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyBatchGenerationResult implements Serializable {

    private static final long serialVersionUID = 1L;
    private List<VocabularyCardDTO> revealedCards;
    private List<VocabularyCardDTO> hiddenCards;
    private int totalRevealed;
    private int totalHidden;
    private int totalCount;
}
