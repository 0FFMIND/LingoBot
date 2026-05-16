package com.lingobot.media.tts.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TextToSpeechService {

    private static final String YOUDAO_TTS_BASE_URL = "https://dict.youdao.com/dictvoice";
    
    private final ConcurrentHashMap<String, CacheEntry> audioCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = TimeUnit.DAYS.toMillis(7);
    
    public enum VoiceType {
        US(0),
        UK(1);
        
        private final int type;
        
        VoiceType(int type) {
            this.type = type;
        }
        
        public int getType() {
            return type;
        }
    }
    
    private record CacheEntry(byte[] data, long timestamp) {}

    @PostConstruct
    public void init() {
        log.info("TextToSpeechService 初始化完成，使用有道词典 TTS API");
        log.info("缓存有效期: {} 天", CACHE_DURATION_MS / TimeUnit.DAYS.toMillis(1));
    }

    public byte[] getWordPronunciation(String word, VoiceType type) {
        if (word == null || word.trim().isEmpty()) {
            log.warn("单词为空，无法获取发音");
            return null;
        }
        
        String cacheKey = word.trim().toLowerCase() + "-" + type.name();
        
        CacheEntry cached = audioCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < CACHE_DURATION_MS) {
            log.info("从缓存获取单词发音: {} ({})", word, type.name());
            return cached.data();
        }
        
        String encodedWord = URLEncoder.encode(word.trim(), StandardCharsets.UTF_8);
        String urlString = YOUDAO_TTS_BASE_URL + "?type=" + type.getType() + "&audio=" + encodedWord;
        
        log.info("获取单词发音: {}, 语音类型: {}", word, type.name());
        log.debug("请求 URL: {}", urlString);
        
        int maxRetries = 3;
        long[] retryDelays = {500, 1000, 2000};
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                connection.setRequestProperty("Accept", "audio/mpeg,audio/*;q=0.9");
                connection.setRequestProperty("Referer", "https://dict.youdao.com/");
                
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    log.warn("获取发音尝试 {}/{} 失败，HTTP 状态码: {}", attempt, maxRetries, responseCode);
                    if (attempt < maxRetries) {
                        Thread.sleep(retryDelays[attempt - 1]);
                        continue;
                    }
                    log.error("获取发音失败，已重试 {} 次，最终 HTTP 状态码: {}", maxRetries, responseCode);
                    return null;
                }
                
                String contentType = connection.getContentType();
                log.debug("响应 Content-Type: {}", contentType);
                
                try (InputStream inputStream = connection.getInputStream();
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    byte[] audioData = outputStream.toByteArray();
                    log.info("获取发音成功，单词: {}, 音频大小: {} bytes, 尝试次数: {}", word, audioData.length, attempt);
                    
                    if (audioData.length > 0) {
                        audioCache.put(cacheKey, new CacheEntry(audioData, System.currentTimeMillis()));
                        log.debug("已缓存单词发音: {} ({})", word, type.name());
                    }
                    
                    return audioData;
                }
                
            } catch (Exception e) {
                log.warn("获取发音尝试 {}/{} 失败: {} - {}", attempt, maxRetries, word, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelays[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("获取单词发音失败，已重试 {} 次: {} - {}", maxRetries, word, e.getMessage(), e);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        return null;
    }
    
    public byte[] getWordPronunciation(String word) {
        return getWordPronunciation(word, VoiceType.US);
    }
    
    public void clearCache() {
        audioCache.clear();
        log.info("TTS 缓存已清空");
    }
    
    public int getCacheSize() {
        return audioCache.size();
    }
}
