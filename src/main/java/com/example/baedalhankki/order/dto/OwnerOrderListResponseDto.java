package com.example.baedalhankki.order.dto;

import java.util.List;

public record OwnerOrderListResponseDto(
        String restaurantName, long pendingCount, List<OwnerOrderSummaryResponseDto> orders) {
    public static OwnerOrderListResponseDto of(String restaurantName, long pendingCount, List<OwnerOrderSummaryResponseDto> orders) {
        return new OwnerOrderListResponseDto(restaurantName, pendingCount, orders);
    }
}
