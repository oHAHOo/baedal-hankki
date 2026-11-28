package com.example.baedalhankki.order.dto;

import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;

import java.util.List;

public record OwnerOrderSummaryResponseDto(
        Long orderId,
        OrderStatus status,
        List<OrderItemSummaryResponseDto> items,
        int totalAmount,
        String paymentMethod,
        String deliveryAddress,
        String addressDetail,
        String contactPhone,
        boolean onHold) {
    public static OwnerOrderSummaryResponseDto from(Order order) {
        return new OwnerOrderSummaryResponseDto(
                order.getId(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemSummaryResponseDto::from).toList(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getDeliveryAddress(),
                order.getAddressDetail(),
                order.getContactPhone(),
                order.isOnHold());
    }
}
