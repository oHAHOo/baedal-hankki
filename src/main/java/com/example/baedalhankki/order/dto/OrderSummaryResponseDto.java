package com.example.baedalhankki.order.dto;

import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;

import java.util.List;

public record OrderSummaryResponseDto(
        Long orderId,
        String restaurantName,
        OrderStatus status,
        List<OrderItemSummaryResponseDto> items,
        int totalAmount,
        String paymentMethod,
        boolean onHold) {
    public static OrderSummaryResponseDto from(Order order) {
        return new OrderSummaryResponseDto(
                order.getId(),
                order.getRestaurantName(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemSummaryResponseDto::from).toList(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.isOnHold());
    }
}
