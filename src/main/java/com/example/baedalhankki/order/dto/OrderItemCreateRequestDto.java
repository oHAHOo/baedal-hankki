package com.example.baedalhankki.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemCreateRequestDto(
        @NotNull @Positive Long menuId, @NotNull @Positive Integer quantity) {}
