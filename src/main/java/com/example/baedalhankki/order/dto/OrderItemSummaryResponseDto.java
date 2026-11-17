package com.example.baedalhankki.order.dto;

import com.example.baedalhankki.order.entity.OrderItem;

public record OrderItemSummaryResponseDto(String menuName, int quantity) {
    public static OrderItemSummaryResponseDto from(OrderItem item) {
        return new OrderItemSummaryResponseDto(
                item.getMenuName(),
                item.getQuantity());
    }
}
