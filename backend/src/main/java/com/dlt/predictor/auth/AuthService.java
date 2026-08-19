package com.dlt.predictor.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 *
 * 处理 PostgreSQL 账户的注册、登录、审核和待审核列表查询。
 *
 * <p>开奖数据仍使用 JPA；账户查询独立通过 MyBatis-Plus 访问 sys_user 表。</p>
 *
 */
@Service
public class AuthService {

    private static final String BOOTSTRAP_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_BOOTSTRAP_ADMIN_PASSWORD = "admin";
    private static final String DUPLICATE_USERNAME_MESSAGE = "用户名已存在";
    private static final String INVALID_CREDENTIALS_MESSAGE = "用户名或密码错误";
    private static final String INACTIVE_ACCOUNT_MESSAGE = "账户尚未通过审核或已禁用";
    private static final String USER_NOT_FOUND_MESSAGE = "用户不存在";

    private final UserAccountMapper userAccountMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AuthService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    /**
     *
     * 初始化默认管理员；仅在数据库尚无同名账户时创建，避免覆盖生产环境凭据。
     *
     */
    @PostConstruct
    void initializeBootstrapAdmin() {
        if (findByUsername(BOOTSTRAP_ADMIN_USERNAME) == null) {
            userAccountMapper.insert(UserAccountEntity.from(createBootstrapAdmin()));
        }
    }

    public void register(String username, String password) {
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException(DUPLICATE_USERNAME_MESSAGE);
        }

        userAccountMapper.insert(UserAccountEntity.from(new UserAccount(
                username,
                passwordEncoder.encode(password),
                UserAccount.Role.USER,
                UserAccount.Status.PENDING,
                false,
                Instant.now())));
    }

    public UserAccount login(String username, String password) {
        UserAccountEntity entity = findByUsername(username);
        if (entity == null) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE);
        }
        UserAccount account = entity.toDomain();
        if (account.status() != UserAccount.Status.APPROVED) {
            throw new IllegalArgumentException(INACTIVE_ACCOUNT_MESSAGE);
        }
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE);
        }
        return account;
    }

    public Page<UserAccountEntity> pendingUsers(int page, int size) {
        Page<UserAccountEntity> result = new Page<>(page, size);
        return userAccountMapper.selectPage(result,
                new QueryWrapper<UserAccountEntity>()
                        .eq("status", UserAccount.Status.PENDING.name())
                        .orderByDesc("created_at"));
    }

    public void approve(String username, boolean approved) {
        int updated = userAccountMapper.update(null,
                new UpdateWrapper<UserAccountEntity>()
                        .eq("username", username)
                        .set("status", approved ? UserAccount.Status.APPROVED.name() : UserAccount.Status.REJECTED.name()));
        if (updated == 0) {
            throw new IllegalArgumentException(USER_NOT_FOUND_MESSAGE);
        }
    }

    private UserAccountEntity findByUsername(String username) {
        return userAccountMapper.selectOne(new QueryWrapper<UserAccountEntity>().eq("username", username));
    }

    private UserAccount createBootstrapAdmin() {
        // 生产环境通过环境变量覆盖默认密码，避免将开发密码带入部署环境。
        String password = System.getenv().getOrDefault(
                "BOOTSTRAP_ADMIN_PASSWORD",
                DEFAULT_BOOTSTRAP_ADMIN_PASSWORD);
        return new UserAccount(
                BOOTSTRAP_ADMIN_USERNAME,
                passwordEncoder.encode(password),
                UserAccount.Role.ADMIN,
                UserAccount.Status.APPROVED,
                true,
                Instant.now());
    }
}
