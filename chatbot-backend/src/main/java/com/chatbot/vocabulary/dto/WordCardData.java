package com.lingobot.vocabulary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordCardData {
    private String word;
    private String phonetic;
    private String meaning;
    private String example;
    private String exampleTranslation;
    private List<String> synonyms;
    private List<String> antonyms;
    private String level;
}
