package com.lingobot.learning.memory.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

@Data
public class ShortTermMemory {
    private static final int MAX_ITEMS = 50;
    private final Deque<MemoryItem> items = new LinkedList<>();

    public void add(MemoryItem item) {
        if (items.size() >= MAX_ITEMS) {
            items.pollFirst();
        }
        items.addLast(item);
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
