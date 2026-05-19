package com.lingobot.infrastructure.tool.dto;

import java.util.Arrays;
import java.util.List;

/**
 * 工具模式常量类。
 *
 * 定义系统支持的对话模式，用于控制工具的可用范围。
 * 不同模式下可调用的工具类别不同。
 */
public final class ToolMode {

    // 普通聊天模式：仅可调用 ONE_TIME 和 ALL 类别的工具
    public static final String CHAT = "chat";

    // Agent 模式：可调用所有类别的工具，支持多轮工具调用链
    public static final String AGENT = "agent";

    // 通用模式列表：包含普通聊天和 Agent 模式
    public static final List<String> GENERAL_MODES = Arrays.asList(CHAT, AGENT);

    private ToolMode() {
    }
}
