package com.example.baedalhankki.order.dto;

import java.util.List;

public record OrderListResponseDto(List<OrderSummaryResponseDto> orders) {
    public static OrderListResponseDto of(List<OrderSummaryResponseDto> orders) {
        return new OrderListResponseDto(orders);
    }
}
