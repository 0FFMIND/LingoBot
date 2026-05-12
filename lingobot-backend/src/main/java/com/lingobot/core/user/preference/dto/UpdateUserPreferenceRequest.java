package com.lingobot.core.user.preference.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPreferenceRequest {
    
    @Pattern(regexp = "^(cefr|ielts|toefl)$", message = "词汇划分标准必须是cefr, ielts 或toefl")
    private String vocabularyCategory;
    
    @Pattern(regexp = "^(a1|a2|b1|b2|c1|c2|4\\.0-5\\.0|5\\.5-6\\.5|7\\.0-8\\.0|8\\.5-9\\.0|60-80|81-100|101-110|111-120)$",
             message = "难度级别无效", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String vocabularyDifficulty;
    
    @Pattern(regexp = "^(qwen|xiaomi)$", message = "模型必须是qwen 或xiaomi")
    private String vocabularyModel;
    
    @Pattern(regexp = "^(qwen|xiaomi)$", message = "模型必须是qwen 或xiaomi")
    private String chatModel;
}
