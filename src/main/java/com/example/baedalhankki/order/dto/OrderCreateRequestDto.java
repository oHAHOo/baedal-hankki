package com.example.baedalhankki.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequestDto(
        @NotNull @Positive Long restaurantId,
        @NotEmpty List<@NotNull @Valid OrderItemCreateRequestDto> items,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotBlank @Size(max = 255) String addressDetail,
        @NotBlank @Pattern(regexp = "010-?[0-9]{4}-?[0-9]{4}") String contactPhone) {}
