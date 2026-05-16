package com.lingobot.learning.memory.core;

public enum MemoryImportance {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int score;

    MemoryImportance(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }
}
