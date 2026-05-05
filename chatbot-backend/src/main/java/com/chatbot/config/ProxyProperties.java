package com.lingobot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {
    
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 7897;
    
    public boolean isValid() {
        return enabled && host != null && !host.isEmpty() && port > 0;
    }
}
