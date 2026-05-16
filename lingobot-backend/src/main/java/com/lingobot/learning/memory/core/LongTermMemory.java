package com.lingobot.learning.memory.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class LongTermMemory {
    private final List<MemoryItem> items = new ArrayList<>();

    public void store(MemoryItem item) {
        items.add(item);
    }

    public List<MemoryItem> retrieveByType(MemoryType type) {
        return items.stream()
                .filter(item -> item.getType() == type)
                .collect(Collectors.toList());
    }

    public List<MemoryItem> retrieveByImportance(MemoryImportance importance) {
        return items.stream()
                .filter(item -> item.getImportance() == importance)
                .collect(Collectors.toList());
    }

    public List<MemoryItem> getAll() {
        return new ArrayList<>(items);
    }

    public void clear() {
        items.clear();
    }

    public int size() {
        return items.size();
    }
}
