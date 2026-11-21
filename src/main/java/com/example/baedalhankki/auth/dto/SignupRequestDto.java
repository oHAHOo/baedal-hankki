package com.example.baedalhankki.auth.dto;

import com.example.baedalhankki.auth.validation.PasswordSize;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequestDto(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[a-z0-9_]{4,50}") String loginId,
        @NotBlank @PasswordSize(minCharacters = 8, maxBytes = 72) String password) {
}
