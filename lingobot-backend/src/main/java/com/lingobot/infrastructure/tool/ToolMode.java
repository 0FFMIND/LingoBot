package com.lingobot.infrastructure.tool;

import java.util.Arrays;
import java.util.List;

// 工具模式常量类
// 定义系统支持的对话模式，用于控制工具的可用范围
public final class ToolMode {

    // 普通聊天模式：仅可调用 ONE_TIME 和 ALL 类别的工具
    public static final String CHAT = "chat";

    // Agent 模式：可调用所有类别的工具，支持多轮工具调用链
    public static final String AGENT = "agent";

    // 词汇学习模式：专有模式，仅用于词汇学习相关的工具调用
    public static final String VOCABULARY = "vocabulary";

    // 通用模式列表：包含普通聊天和 Agent 模式
    public static final List<String> GENERAL_MODES = Arrays.asList(CHAT, AGENT);

    // 专有模式列表：包含词汇学习等特定业务场景模式
    public static final List<String> EXCLUSIVE_MODES = Arrays.asList(VOCABULARY);

    private ToolMode() {
    }

    // 检查是否为通用模式
    public static boolean isGeneralMode(String mode) {
        return GENERAL_MODES.contains(mode);
    }

    // 检查是否为专有模式
    public static boolean isExclusiveMode(String mode) {
        return EXCLUSIVE_MODES.contains(mode);
    }
}
