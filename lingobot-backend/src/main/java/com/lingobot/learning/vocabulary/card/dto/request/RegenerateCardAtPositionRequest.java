package com.lingobot.learning.vocabulary.card.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerateCardAtPositionRequest {
    private Integer position;
    private String category;
    private String difficulty;
}
