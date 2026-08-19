package com.dlt.predictor.auth;

/**
 *
 * 登录成功后的 MVP 响应；令牌格式后续可替换为正式 JWT 而不改变接口字段。
 *
 */
public record LoginResponse(
        String accessToken,
        String role,
        boolean mustChangePassword) {
}
