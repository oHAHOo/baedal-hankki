package com.example.baedalhankki.order.dto;

import com.example.baedalhankki.order.entity.OrderItem;

public record OrderItemDetailResponseDto(String menuName, int unitPrice, int quantity, int itemAmount) {
    public static OrderItemDetailResponseDto from(OrderItem item) {
        return new OrderItemDetailResponseDto(
                item.getMenuName(),
                item.getUnitPrice(),
                item.getQuantity(),
                Math.multiplyExact(item.getUnitPrice(), item.getQuantity()));
    }
}
