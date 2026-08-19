package com.dlt.predictor.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 *
 * 注册与登录共用的凭据输入，并在 HTTP 边界约束账号和密码长度。
 *
 */
public record CredentialsRequest(
        @NotBlank @Size(min = 3, max = 40) String username,
        @NotBlank @Size(min = 5, max = 72) String password) {
}
