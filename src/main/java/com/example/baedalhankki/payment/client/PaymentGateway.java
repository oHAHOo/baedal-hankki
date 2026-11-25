package com.example.baedalhankki.payment.client;

import java.util.Optional;

public interface PaymentGateway {
    void checkAvailable();

    String getClientKey();

    Optional<ProviderPayment> find(String providerOrderId);

    ProviderPayment confirm(String paymentKey, String providerOrderId, int amount, String idempotencyKey);

    ProviderPayment cancel(String paymentKey, String idempotencyKey);
}
