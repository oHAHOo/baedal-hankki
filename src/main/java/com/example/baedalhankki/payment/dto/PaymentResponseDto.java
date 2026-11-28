package com.example.baedalhankki.payment.dto;

import com.example.baedalhankki.payment.entity.Payment;
import com.example.baedalhankki.payment.entity.PaymentStatus;

public record PaymentResponseDto(Long orderId, String providerOrderId, int amount, PaymentStatus status,
        boolean cancellationRequested) {
    public static PaymentResponseDto from(Payment payment) {
        return new PaymentResponseDto(payment.getOrderId(), payment.getProviderOrderId(),
                payment.getAmount(), payment.getStatus(), payment.isCancellationRequested());
    }
}
