package com.example.baedalhankki.global.security;

import com.example.baedalhankki.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpSessionSecurityContextRepository contextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository repository, ObjectMapper mapper)
            throws Exception {
        return http.csrf(c -> c.disable())
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .logout(c -> c.disable())
                .securityContext(c -> c.securityContextRepository(repository))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers("/api/auth/login", "/api/auth/signup")
                                        .permitAll()
                                        .requestMatchers("/api/owner/**")
                                        .hasRole("OWNER")
                                        .requestMatchers("/api/orders/**")
                                        .hasRole("CUSTOMER")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (request, response, exception) -> writeError(response, mapper, ErrorCode.UNAUTHENTICATED))
                                        .accessDeniedHandler(
                                                (request, response, exception) -> writeError(response, mapper, ErrorCode.FORBIDDEN)))
                .build();
    }

    private void writeError(HttpServletResponse response, ObjectMapper mapper, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), Map.of("code", errorCode.name(), "message", errorCode.getMessage()));
    }
}
