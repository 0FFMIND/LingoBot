package com.lingobot.media.tts.controller;

import com.lingobot.media.tts.service.TextToSpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TextToSpeechService textToSpeechService;

    @GetMapping("/word")
    public ResponseEntity<byte[]> getWordPronunciation(
            @RequestParam String word,
            @RequestParam(required = false, defaultValue = "us") String voiceType) {
        
        log.info("获取单词发音 API  单词: {}, 语音类型: {}", word, voiceType);
        
        TextToSpeechService.VoiceType type;
        try {
            type = TextToSpeechService.VoiceType.valueOf(voiceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的语音类�? {}, 使用默认: US", voiceType);
            type = TextToSpeechService.VoiceType.US;
        }
        
        byte[] audioData = textToSpeechService.getWordPronunciation(word, type);
        
        if (audioData == null || audioData.length == 0) {
            log.error("无法获取单词发音: {}", word);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audioData.length);
        headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS));
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(audioData);
    }
}
