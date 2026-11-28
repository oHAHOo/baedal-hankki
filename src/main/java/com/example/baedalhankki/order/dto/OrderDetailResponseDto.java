package com.example.baedalhankki.order.dto;

import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;

import java.util.List;

public record OrderDetailResponseDto(
        Long orderId,
        String restaurantName,
        OrderStatus status,
        List<OrderItemDetailResponseDto> items,
        int itemsAmount,
        int deliveryFee,
        int totalAmount,
        String paymentMethod,
        String deliveryAddress,
        String addressDetail,
        String contactPhone,
        boolean onHold) {
    public static OrderDetailResponseDto from(Order order) {
        return new OrderDetailResponseDto(
                order.getId(),
                order.getRestaurantName(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemDetailResponseDto::from).toList(),
                order.getItemsAmount(),
                order.getDeliveryFee(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getDeliveryAddress(),
                order.getAddressDetail(),
                order.getContactPhone(),
                order.isOnHold());
    }
}
