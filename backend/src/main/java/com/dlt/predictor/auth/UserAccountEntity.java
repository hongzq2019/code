package com.dlt.predictor.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 *
 * MyBatis-Plus 对 sys_user 表的账户映射，认证逻辑仍通过 UserAccount 领域对象处理。
 *
 */
@TableName("sys_user")
public class UserAccountEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    private String role;

    private String status;

    @TableField("must_change_password")
    private boolean mustChangePassword;

    @TableField("created_at")
    private Instant createdAt;

    /**
     *
     * 将数据库字符串状态转换为认证模块使用的领域对象。
     *
     */
    UserAccount toDomain() {
        return new UserAccount(
                username,
                passwordHash,
                UserAccount.Role.valueOf(role),
                UserAccount.Status.valueOf(status),
                mustChangePassword,
                createdAt);
    }

    /**
     *
     * 将认证模块的新账户数据转换为可插入的数据库实体。
     *
     */
    static UserAccountEntity from(UserAccount account) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUsername(account.username());
        entity.setPasswordHash(account.passwordHash());
        entity.setRole(account.role().name());
        entity.setStatus(account.status().name());
        entity.setMustChangePassword(account.mustChangePassword());
        entity.setCreatedAt(account.createdAt());
        return entity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
