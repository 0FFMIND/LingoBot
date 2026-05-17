package com.lingobot.media.tts.controller;

import com.lingobot.media.tts.service.TextToSpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 文本转语音控制器。
 *
 * @RestController 提供 REST API 接口，
 * @RequestMapping("/api/tts") 统一前缀，负责处理单词发音请求，
 * 调用 TextToSpeechService 从有道词典 TTS API 获取单词发音音频，
 * 支持美式发音（US）和英式发音（UK），并设置 7 天的 HTTP 缓存。
 */
@Slf4j
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TextToSpeechService textToSpeechService;

    // 获取单词发音音频，支持指定美式或英式发音，返回 MP3 格式音频数据
    @GetMapping("/word")
    public ResponseEntity<byte[]> getWordPronunciation(
            @RequestParam String word,
            @RequestParam(required = false, defaultValue = "us") String voiceType) {
        
        log.info("获取单词发音 API  单词: {}, 语音类型: {}", word, voiceType);
        
        TextToSpeechService.VoiceType type;
        try {
            type = TextToSpeechService.VoiceType.valueOf(voiceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的语音类型 {}, 使用默认: US", voiceType);
            type = TextToSpeechService.VoiceType.US;
        }
        
        byte[] audioData = textToSpeechService.getWordPronunciation(word, type);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audioData.length);
        headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS));
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(audioData);
    }
}
