package com.dlt.predictor.auth;

import com.dlt.predictor.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 认证 API：注册账户、验证登录凭据及提供当前 MVP 的令牌占位接口。
 *
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REGISTRATION_PENDING_MESSAGE = "注册已提交，等待管理员审核";
    private static final String DEVELOPMENT_TOKEN_PREFIX = "dev-";
    private static final String REFRESHED_DEVELOPMENT_TOKEN = "dev-refreshed-token";

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ApiResponse<RegistrationResponse> register(@Valid @RequestBody CredentialsRequest request) {
        authService.register(request.username(), request.password());
        return ApiResponse.ok(new RegistrationResponse(REGISTRATION_PENDING_MESSAGE));
    }

    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@Valid @RequestBody CredentialsRequest request) {
        UserAccount account = authService.login(request.username(), request.password());
        LoginResponse response = new LoginResponse(
                DEVELOPMENT_TOKEN_PREFIX + account.username(),
                account.role().name(),
                account.mustChangePassword());
        return ApiResponse.ok(response);
    }

    @PostMapping("/refresh")
    ApiResponse<TokenResponse> refresh() {
        return ApiResponse.ok(new TokenResponse(REFRESHED_DEVELOPMENT_TOKEN));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }
}
