package com.lingobot.audio.service;

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

@Slf4j
@Service
public class AudioConversionService {
    
    private static final Set<String> SUPPORTED_FORMATS = Set.of("mp3", "flac", "m4a", "wav", "ogg");
    private static final String TARGET_FORMAT = "wav";
    
    public boolean needsConversion(String format) {
        if (format == null) {
            return true;
        }
        String normalized = normalizeFormat(format);
        return !SUPPORTED_FORMATS.contains(normalized);
    }
    
    public ConversionResult convertIfNeeded(String base64Audio, String sourceFormat) {
        if (!needsConversion(sourceFormat)) {
            log.info("音频格式 {} 已支持，无需转换", sourceFormat);
            return new ConversionResult(base64Audio, normalizeFormat(sourceFormat));
        }
        
        log.info("开始转换音频格�? {} -> {}", sourceFormat, TARGET_FORMAT);
        
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
            
            log.info("音频转换完成，原始大�? {} bytes, 转换�? {} bytes", 
                    audioBytes.length, convertedBytes.length);
            
            return new ConversionResult(convertedBase64, TARGET_FORMAT);
            
        } catch (Exception e) {
            log.error("音频转换失败: {}", e.getMessage(), e);
            log.warn("尝试使用原始格式继续: {}", sourceFormat);
            return new ConversionResult(base64Audio, normalizeFormat(sourceFormat));
        } finally {
            deleteTempFile(tempSource);
            deleteTempFile(tempTarget);
        }
    }
    
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
    
    private String getCodecForFormat(String format) {
        return switch (format.toLowerCase()) {
            case "mp3" -> "libmp3lame";
            case "m4a" -> "aac";
            case "ogg" -> "libvorbis";
            case "flac" -> "flac";
            default -> "pcm_s16le";
        };
    }
    
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
    
    private File createTempFile(String prefix, String suffix) throws IOException {
        Path tempDir = Files.createTempDirectory("audio_conv_");
        return File.createTempFile(prefix, suffix, tempDir.toFile());
    }
    
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
    
    public record ConversionResult(String base64Audio, String format) {}
}
