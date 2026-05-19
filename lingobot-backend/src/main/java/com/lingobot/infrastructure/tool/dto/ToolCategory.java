package com.lingobot.infrastructure.tool.dto;

/**
 * 工具类别枚举。
 *
 * 用于定义工具的使用场景，控制工具在不同对话模式下的可见性。
 * 每个工具通过 ToolHandler.getCategory() 返回其所属类别，
 * 系统根据当前对话模式过滤出可调用的工具列表。
 */
public enum ToolCategory {

    // 一次性工具：执行一次即完成，普通聊天模式可用
    ONE_TIME,

    // Agent 专用工具：仅在 Agent 模式下可用，支持多轮调用链
    AGENT_ONLY,

    // 所有模式可用：通用型工具，在任何对话模式下都可调用
    ALL
}
