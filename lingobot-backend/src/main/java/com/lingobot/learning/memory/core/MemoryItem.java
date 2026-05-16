package com.lingobot.learning.memory.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {
    private String id;
    private MemoryType type;
    private MemoryTier tier;
    private MemoryImportance importance;
    private String content;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private int accessCount;
}
