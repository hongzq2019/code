package com.dlt.predictor.auth;

import java.time.Instant;

/**
 *
 * 管理员审核页展示的待审核用户摘要，刻意不暴露密码散列。
 *
 */
public record PendingUserResponse(
        String username,
        String role,
        String status,
        boolean mustChangePassword,
        Instant createdAt) {

    static PendingUserResponse from(UserAccount user) {
        return new PendingUserResponse(
                user.username(),
                user.role().name(),
                user.status().name(),
                user.mustChangePassword(),
                user.createdAt());
    }

    static PendingUserResponse from(UserAccountEntity user) {
        return new PendingUserResponse(
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt());
    }
}
