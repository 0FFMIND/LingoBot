package com.lingobot.learning.llm.tool.service;

import java.util.Arrays;
import java.util.List;

/**
 * MCP 工具模式常量类 * 定义系统支持的对话模式，用于控制工具的可用范围 */
public final class ToolMode {
    
    /** 普通聊天模式*/
    public static final String CHAT = "chat";
    /** Agent 模式：支持更复杂的工具调用链 */
    public static final String AGENT = "agent";
    /** 词汇学习模式 */
    public static final String VOCABULARY = "vocabulary";
    
    /** 通用模式列表 */
    public static final List<String> GENERAL_MODES = Arrays.asList(CHAT, AGENT);
    /** 专有模式列表 */
    public static final List<String> EXCLUSIVE_MODES = Arrays.asList(VOCABULARY);
    
    private ToolMode() {
    }
    
    /**
     * 检查是否为通用模式
     */
    public static boolean isGeneralMode(String mode) {
        return GENERAL_MODES.contains(mode);
    }
    
    /**
     * 检查是否为专有模式
     */
    public static boolean isExclusiveMode(String mode) {
        return EXCLUSIVE_MODES.contains(mode);
    }
}
