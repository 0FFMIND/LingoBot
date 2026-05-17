package com.lingobot.core.user.preference.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户偏好设置请求 DTO。
 *
 * 用于接收用户更新偏好设置的请求，所有字段均为可选（只更新传入的字段）。
 * 每个字段都有正则表达式验证，确保输入值合法。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPreferenceRequest {
    
    // 词汇划分标准，可选值：cefr（欧洲语言共同参考框架）、ielts（雅思）、toefl（托福）
    @Pattern(regexp = "^(cefr|ielts|toefl)$", message = "词汇划分标准必须是cefr, ielts 或toefl")
    private String vocabularyCategory;
    
    // 词汇难度级别，根据不同划分标准有不同的取值范围
    @Pattern(regexp = "^(a1|a2|b1|b2|c1|c2|4\\.0-5\\.0|5\\.5-6\\.5|7\\.0-8\\.0|8\\.5-9\\.0|60-80|81-100|101-110|111-120)$",
             message = "难度级别无效", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String vocabularyDifficulty;
    
    // 词汇学习使用的 AI 模型，可选值：qwen（通义千问）、xiaomi（小米）
    @Pattern(regexp = "^(qwen|xiaomi)$", message = "模型必须是qwen 或xiaomi")
    private String vocabularyModel;
    
    // 聊天使用的 AI 模型，可选值：qwen（通义千问）、xiaomi（小米）
    @Pattern(regexp = "^(qwen|xiaomi)$", message = "模型必须是qwen 或xiaomi")
    private String chatModel;
}
