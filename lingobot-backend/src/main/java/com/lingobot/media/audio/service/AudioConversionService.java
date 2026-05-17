package com.lingobot.media.audio.service;

import com.lingobot.infrastructure.common.exception.MediaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;

/**
 * 音频格式转换服务。
 *
 * @Service 标记为 Spring 服务组件，
 * 使用 ws.schild.jave 库将不支持的音频格式（如 webm、opus 等）
 * 转换为标准 wav 格式，确保后续语音识别等处理能够正常工作。
 * 转换过程中使用临时文件存储中间结果，完成后自动清理。
 */
@Slf4j
@Service
public class AudioConversionService {
    
    private static final Set<String> SUPPORTED_FORMATS = Set.of("mp3", "flac", "m4a", "wav", "ogg");
    private static final String TARGET_FORMAT = "wav";
    
    // 判断给定音频格式是否需要转换，不在支持列表中的格式需要转换
    public boolean needsConversion(String format) {
        if (format == null) {
            return true;
        }
        String normalized = normalizeFormat(format);
        return !SUPPORTED_FORMATS.contains(normalized);
    }
    
    // 按需转换音频格式，输入 Base64 编码的音频数据和源格式，返回转换后的结果
    public ConversionResult convertIfNeeded(String base64Audio, String sourceFormat) {
        if (!needsConversion(sourceFormat)) {
            log.info("音频格式 {} 已支持，无需转换", sourceFormat);
            return new ConversionResult(base64Audio, normalizeFormat(sourceFormat));
        }
        
        log.info("开始转换音频格式 {} -> {}", sourceFormat, TARGET_FORMAT);
        
        File tempSource = null;
        File tempTarget = null;
        
        try {
            byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
            
            tempSource = createTempFile("audio_source_", getFileExtension(sourceFormat));
            try (FileOutputStream fos = new FileOutputStream(tempSource)) {
                fos.write(audioBytes);
            }
            
            tempTarget = createTempFile("audio_target_", "." + TARGET_FORMAT);
            
            convertAudio(tempSource, tempTarget, TARGET_FORMAT);
            
            byte[] convertedBytes = Files.readAllBytes(tempTarget.toPath());
            String convertedBase64 = Base64.getEncoder().encodeToString(convertedBytes);
            
            log.info("音频转换完成，原始大小 {} bytes, 转换后 {} bytes",
                    audioBytes.length, convertedBytes.length);
            
            return new ConversionResult(convertedBase64, TARGET_FORMAT);
            
        } catch (Exception e) {
            log.error("音频转换失败: {}", e.getMessage(), e);
            throw MediaException.audioConversionFailed("音频转换失败: " + e.getMessage());
        } finally {
            deleteTempFile(tempSource);
            deleteTempFile(tempTarget);
        }
    }
    
    // 使用 JAVE 编码器将源音频文件转换为目标格式
    private void convertAudio(File source, File target, String targetFormat) throws EncoderException {
        MultimediaObject multimediaObject = new MultimediaObject(source);
        
        AudioAttributes audioAttributes = new AudioAttributes();
        audioAttributes.setCodec(getCodecForFormat(targetFormat));
        
        EncodingAttributes encodingAttributes = new EncodingAttributes();
        encodingAttributes.setOutputFormat(targetFormat);
        encodingAttributes.setAudioAttributes(audioAttributes);
        
        Encoder encoder = new Encoder();
        encoder.encode(multimediaObject, target, encodingAttributes);
    }
    
    // 根据目标音频格式获取对应的编码器名称
    private String getCodecForFormat(String format) {
        return switch (format.toLowerCase()) {
            case "mp3" -> "libmp3lame";
            case "m4a" -> "aac";
            case "ogg" -> "libvorbis";
            case "flac" -> "flac";
            default -> "pcm_s16le";
        };
    }
    
    // 根据音频格式字符串提取对应的文件扩展名
    private String getFileExtension(String format) {
        if (format == null) {
            return ".webm";
        }
        String lowerFormat = format.toLowerCase();
        if (lowerFormat.contains("webm") || lowerFormat.contains("opus")) {
            return ".webm";
        }
        if (lowerFormat.contains("mp4")) {
            return ".mp4";
        }
        return "." + lowerFormat.split(";")[0].trim();
    }
    
    // 创建带唯一前缀和后缀的临时文件，用于音频转换过程中的中间存储
    private File createTempFile(String prefix, String suffix) throws IOException {
        Path tempDir = Files.createTempDirectory("audio_conv_");
        return File.createTempFile(prefix, suffix, tempDir.toFile());
    }
    
    // 删除临时文件及其所在的空目录，避免磁盘空间泄漏
    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
                File parentDir = file.getParentFile();
                if (parentDir != null && parentDir.isDirectory() && parentDir.list().length == 0) {
                    parentDir.delete();
                }
            } catch (Exception e) {
                log.warn("无法删除临时文件: {}", file.getAbsolutePath(), e);
            }
        }
    }
    
    // 将各种格式的 MIME 类型或描述字符串规范化为标准的格式标识
    private String normalizeFormat(String format) {
        if (format == null) {
            return "wav";
        }
        String lowerFormat = format.toLowerCase();
        
        if (lowerFormat.contains("webm") || lowerFormat.contains("opus")) {
            return "webm";
        }
        if (lowerFormat.contains("mp3")) {
            return "mp3";
        }
        if (lowerFormat.contains("m4a") || lowerFormat.contains("mp4")) {
            return "m4a";
        }
        if (lowerFormat.contains("wav")) {
            return "wav";
        }
        if (lowerFormat.contains("flac")) {
            return "flac";
        }
        if (lowerFormat.contains("ogg") || lowerFormat.contains("oga")) {
            return "ogg";
        }
        if (lowerFormat.contains("aiff")) {
            return "aiff";
        }
        
        log.warn("未知音频格式: {}, 使用默认 wav", format);
        return "wav";
    }
    
    // 音频转换结果记录，包含转换后的 Base64 音频数据和实际格式
    public record ConversionResult(String base64Audio, String format) {}
}
