package com.example.baedalhankki.order.dto;

import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;

public record OrderStateResponseDto(Long orderId, OrderStatus status) {
    public static OrderStateResponseDto from(Order order) {
        return new OrderStateResponseDto(
                order.getId(),
                order.getStatus());
    }
}
