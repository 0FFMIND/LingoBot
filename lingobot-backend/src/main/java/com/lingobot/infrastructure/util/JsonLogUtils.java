package com.lingobot.infrastructure.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON日志工具类，用于将对象转换为适合日志记录的字符串格式
 */
@Slf4j
public class JsonLogUtils {
    
    private static final int LONG_STRING_THRESHOLD = 100;
    
    /**
     * 将对象转换为适合日志记录的字符串格式
     * @param objectMapper ObjectMapper实例
     * @param obj 要转换的对象
     * @return 适合日志记录的字符串
     */
    public static String toLogString(ObjectMapper objectMapper, Object obj) {
        try {
            JsonNode root = objectMapper.valueToTree(obj);
            processNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to serialize object for log: {}", e.getMessage());
            return String.valueOf(obj);
        }
    }
    
    /**
     * 递归处理JSON节点，将长字符串替换为摘要信息
     * @param node 要处理的JSON节点
     */
    private static void processNode(JsonNode node) {
        if (node == null) return;
        
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            
            objectNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                
                if ("data".equals(fieldName) && fieldValue.isTextual()) {
                    String text = fieldValue.asText();
                    if (text.length() > LONG_STRING_THRESHOLD) {
                        objectNode.put(fieldName, "[base64 data, length: " + text.length() + "]");
                    }
                } else {
                    processNode(fieldValue);
                }
            });
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                processNode(arrayNode.get(i));
            }
        }
    }
}
