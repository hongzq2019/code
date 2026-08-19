package com.dlt.predictor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 * 确保管理员查看待审用户时不会暴露密码哈希。
 *
 */
class PendingUserResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void doesNotSerializePasswordHashForAdministratorReview() throws Exception {
        UserAccount account = new UserAccount(
                "reader",
                "$2a$10$secret-hash",
                UserAccount.Role.USER,
                UserAccount.Status.PENDING,
                false,
                Instant.parse("2025-01-01T00:00:00Z"));

        String json = objectMapper.writeValueAsString(PendingUserResponse.from(account));

        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("secret-hash"));
    }
}
