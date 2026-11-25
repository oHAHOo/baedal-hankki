package com.example.baedalhankki.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderPayment(
        String paymentKey, String orderId, Integer totalAmount, Integer balanceAmount,
        String currency, String status) {
}
