package com.dlt.predictor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 *
 * Spring Security 基础配置；当前 MVP 允许所有接口访问，便于前后端联调。
 *
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 正式启用鉴权时，应将 anyRequest().permitAll() 收紧为按角色授权规则。
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/api/v1/auth/**", "/api/v1/dlt/draws/**")
                        .permitAll()
                        .anyRequest()
                        .permitAll())
                .build();
    }
}
