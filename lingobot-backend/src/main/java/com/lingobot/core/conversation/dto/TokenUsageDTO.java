package com.lingobot.core.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Token 使用统计数据传输对象。
 *
 * 用于记录和传递 LLM 调用的 token 使用量，
 * 包含输入 token、输出 token 和总 token 数，
 * 支持序列化以便在分布式环境中传输。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageDTO implements Serializable {

    // 序列化版本号
    private static final long serialVersionUID = 1L;

    // 输入 token 数（用户消息 + 上下文）
    private Integer promptTokens;
    // 输出 token 数（AI 响应）
    private Integer completionTokens;
    // 总 token 数
    private Integer totalTokens;

    // 判断是否所有 token 字段都为空
    public boolean isEmpty() {
        return promptTokens == null && completionTokens == null && totalTokens == null;
    }

    // 获取总 token 数，优先使用 totalTokens，否则计算两者之和
    public Integer getTotal() {
        if (totalTokens != null) {
            return totalTokens;
        }
        int sum = 0;
        if (promptTokens != null) sum += promptTokens;
        if (completionTokens != null) sum += completionTokens;
        return sum > 0 ? sum : null;
    }
}
