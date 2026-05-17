package com.lingobot.media.tts.service;

import com.lingobot.infrastructure.common.exception.MediaException;
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

/**
 * 文本转语音服务。
 *
 * @Service 标记为 Spring 服务组件，
 * 调用有道词典 TTS API 获取英语单词的发音音频，
 * 支持美式发音（US）和英式发音（UK）两种语音类型，
 * 内置 7 天有效期的内存缓存，避免重复请求外部 API，
 * 请求失败时自动重试最多 3 次，提高服务稳定性。
 */
@Slf4j
@Service
public class TextToSpeechService {

    private static final String YOUDAO_TTS_BASE_URL = "https://dict.youdao.com/dictvoice";
    
    private final ConcurrentHashMap<String, CacheEntry> audioCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = TimeUnit.DAYS.toMillis(7);
    
    // 语音类型枚举：US 美式发音，UK 英式发音，对应有道 API 的 type 参数
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
    
    // 缓存条目记录，包含音频数据和缓存时间戳
    private record CacheEntry(byte[] data, long timestamp) {}

    // 服务初始化方法，启动时打印配置信息
    @PostConstruct
    public void init() {
        log.info("TextToSpeechService 初始化完成，使用有道词典 TTS API");
        log.info("缓存有效期: {} 天", CACHE_DURATION_MS / TimeUnit.DAYS.toMillis(1));
    }

    // 获取指定单词的发音音频，优先从缓存读取，缓存未命中则请求有道 TTS API，最多重试 3 次
    public byte[] getWordPronunciation(String word, VoiceType type) {
        if (word == null || word.trim().isEmpty()) {
            throw MediaException.badRequest("单词为空，无法获取发音");
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
                    throw MediaException.ttsPronunciationFailed("获取发音失败，请稍后重试");
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
                        throw MediaException.ttsPronunciationFailed("获取发音失败：线程被中断");
                    }
                } else {
                    log.error("获取单词发音失败，已重试 {} 次: {} - {}", maxRetries, word, e.getMessage(), e);
                    throw MediaException.ttsPronunciationFailed("获取发音失败，请稍后重试");
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        throw MediaException.ttsPronunciationFailed("获取发音失败，请稍后重试");
    }
    
    // 获取单词发音的重载方法，默认使用美式发音
    public byte[] getWordPronunciation(String word) {
        return getWordPronunciation(word, VoiceType.US);
    }
    
    // 清空所有发音缓存，用于手动刷新或释放内存
    public void clearCache() {
        audioCache.clear();
        log.info("TTS 缓存已清空");
    }
    
    // 获取当前缓存中的发音数量，用于监控缓存使用情况
    public int getCacheSize() {
        return audioCache.size();
    }
}
