package com.lingobot.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP地址工具类，用于获取客户端真实IP地址
 */
@Slf4j
public class IpUtils {
    
    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP",
        "HTTP_X_FORWARDED_FOR"
    };
    
    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    private static final String COMMA = ",";
    
    /**
     * 从HTTP请求中获取客户端真实IP地址
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        
        String ip = null;
        
        for (String header : IP_HEADERS) {
            ip = request.getHeader(header);
            if (isValidIp(ip)) {
                break;
            }
        }
        
        if (!isValidIp(ip)) {
            ip = request.getRemoteAddr();
            
            if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
                try {
                    InetAddress inet = InetAddress.getLocalHost();
                    ip = inet.getHostAddress();
                } catch (UnknownHostException e) {
                    log.warn("无法获取本地主机地址", e);
                }
            }
        }
        
        if (ip != null && ip.contains(COMMA)) {
            String[] ipArray = ip.split(COMMA);
            for (String ipStr : ipArray) {
                if (isValidIp(ipStr.trim())) {
                    ip = ipStr.trim();
                    break;
                }
            }
        }
        
        return ip != null ? ip : UNKNOWN;
    }
    
    /**
     * 检查IP地址是否有效
     * @param ip IP地址字符串
     * @return 是否有效
     */
    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
