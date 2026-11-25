package com.example.baedalhankki.payment.dto;

import com.example.baedalhankki.order.dto.OrderStateResponseDto;

public record PaymentOperation(OrderStateResponseDto order, PaymentResponseDto payment, String paymentKey) {
}
