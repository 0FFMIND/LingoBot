package com.lingobot.media.audio.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/audio")
public class AudioController {

    private static final String AUDIO_BASE_PATH = "static/audio/";

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getAudio(@PathVariable String filename) {
        log.info("Requesting audio file: {}", filename);
        
        String filePath = AUDIO_BASE_PATH + filename;
        Resource resource = new ClassPathResource(filePath);
        
        if (!resource.exists()) {
            log.error("Audio file not found: {}", filePath);
            return ResponseEntity.notFound().build();
        }
        
        String contentType = getContentType(filename);
        log.info("Audio file found, content-type: {}", contentType);
        
        try {
            long contentLength = resource.contentLength();
            log.info("Audio file size: {} bytes", contentLength);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(resource);
        } catch (IOException e) {
            log.error("Error reading audio file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getContentType(String filename) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerName.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (lowerName.endsWith(".m4a")) {
            return "audio/mp4";
        }
        return "application/octet-stream";
    }
}
