package com.example.baedalhankki.payment.dto;

import com.example.baedalhankki.payment.entity.Payment;

public record PaymentPrepareResponseDto(
        Long orderId, String providerOrderId, int amount, String orderName, String clientKey) {
    public static PaymentPrepareResponseDto of(Payment payment, String clientKey) {
        return new PaymentPrepareResponseDto(payment.getOrderId(), payment.getProviderOrderId(),
                payment.getAmount(), "배달한끼 주문 " + payment.getOrderId(), clientKey);
    }
}
