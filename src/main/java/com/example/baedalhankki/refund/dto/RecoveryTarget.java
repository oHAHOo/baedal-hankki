package com.example.baedalhankki.refund.dto;

import com.example.baedalhankki.payment.entity.Payment;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import java.time.Instant;

public record RecoveryTarget(Long orderId, String providerOrderId, String paymentKey, int amount,
        PaymentStatus status, boolean cancellationRequested, Instant preparedAt,
        Instant confirmationStartedAt, Instant cancellationStartedAt, String token) {
    public static RecoveryTarget of(Payment payment, String token) {
        return new RecoveryTarget(payment.getOrderId(), payment.getProviderOrderId(), payment.getPaymentKey(),
                payment.getAmount(), payment.getStatus(), payment.isCancellationRequested(), payment.getPreparedAt(),
                payment.getConfirmationStartedAt(), payment.getCancellationStartedAt(), token);
    }
}
