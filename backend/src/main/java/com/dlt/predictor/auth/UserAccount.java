package com.dlt.predictor.auth;

import java.time.Instant;

/**
 *
 * 内存账户模型；密码仅以 BCrypt 散列形式保存。
 *
 */
record UserAccount(
        String username,
        String passwordHash,
        Role role,
        Status status,
        boolean mustChangePassword,
        Instant createdAt) {

    enum Role {
        /**
         *
         * 系统管理权限。
         *
         */
        ADMIN,
        /**
         *
         * 普通分析用户权限。
         *
         */
        USER
    }

    enum Status {
        /**
         *
         * 已提交注册、等待审核。
         *
         */
        PENDING,
        /**
         *
         * 可正常登录。
         *
         */
        APPROVED,
        /**
         *
         * 被管理员拒绝。
         *
         */
        REJECTED,
        /**
         *
         * 被禁用，禁止登录。
         *
         */
        DISABLED
    }
}
