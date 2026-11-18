package com.example.baedalhankki.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @NotBlank @Size(max = 50) String loginId, @NotBlank @Size(max = 100) String password) {}
