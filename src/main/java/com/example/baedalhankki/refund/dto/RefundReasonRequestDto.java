package com.example.baedalhankki.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundReasonRequestDto(@NotBlank @Size(max = 500) String reason) {
}
