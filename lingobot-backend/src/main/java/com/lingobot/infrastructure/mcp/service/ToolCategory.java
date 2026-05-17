package com.lingobot.infrastructure.mcp.service;

/**
 * MCP 工具类别枚举
 * 用于定义工具的使用场景 */
public enum ToolCategory {
    
    /** 一次性工具：执行一次即完成 */
    ONE_TIME,
    
    /** Agent 专用工具：仅在Agent 模式下可用*/
    AGENT_ONLY,
    
    /** 所有模式可用*/
    ALL
}
