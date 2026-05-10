package com.lingobot.core.user.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginWithCodeRequest {
    private String email;
    private String password;
    private String verificationCode;
}
