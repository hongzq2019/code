package com.dlt.predictor.auth;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * 覆盖账户从注册待审到审批后可登录的主流程。
 *
 */
class AuthServiceTest {

    @Test
    void registersPendingUserThenAllowsLoginAfterApproval() {
        UserAccountMapper mapper = mock(UserAccountMapper.class);
        UserAccountEntity account = pendingAccount();
        when(mapper.selectOne(any())).thenReturn(null, account, account);
        when(mapper.insert(any(UserAccountEntity.class))).thenReturn(1);
        when(mapper.update(isNull(), any())).thenReturn(1);

        AuthService service = new AuthService(mapper);
        service.register("reader", "password1");

        assertThrows(IllegalArgumentException.class, () -> service.login("reader", "password1"));

        service.approve("reader", true);
        account.setStatus(UserAccount.Status.APPROVED.name());

        assertEquals("reader", service.login("reader", "password1").username());
    }

    private UserAccountEntity pendingAccount() {
        UserAccountEntity account = new UserAccountEntity();
        account.setUsername("reader");
        account.setPasswordHash(new BCryptPasswordEncoder().encode("password1"));
        account.setRole(UserAccount.Role.USER.name());
        account.setStatus(UserAccount.Status.PENDING.name());
        account.setCreatedAt(Instant.now());
        return account;
    }
}
