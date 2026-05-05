package com.lingobot.userpreference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceDTO {
    
    private Long id;
    private Long userId;
    
    private String vocabularyCategory;
    private String vocabularyDifficulty;
    private String vocabularyModel;
    private String chatModel;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
