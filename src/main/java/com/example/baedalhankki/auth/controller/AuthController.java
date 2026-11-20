package com.example.baedalhankki.auth.controller;

import lombok.RequiredArgsConstructor;

import com.example.baedalhankki.auth.dto.LoginRequestDto;
import com.example.baedalhankki.auth.dto.RoleResponseDto;
import com.example.baedalhankki.auth.dto.SignupRequestDto;
import com.example.baedalhankki.auth.service.AuthService;
import com.example.baedalhankki.global.security.AuthMember;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final HttpSessionSecurityContextRepository repository;

    @PostMapping("/api/auth/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequestDto request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<RoleResponseDto> login(
            @Valid @RequestBody LoginRequestDto body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthMember member = authService.login(body.loginId(), body.password());
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        member,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + member.role().name()))));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        return ResponseEntity.ok(RoleResponseDto.of(member.role()));
    }

    @GetMapping("/api/members/me")
    public ResponseEntity<RoleResponseDto> me(@AuthenticationPrincipal AuthMember member) {
        return ResponseEntity.ok(RoleResponseDto.of(member.role()));
    }
}
