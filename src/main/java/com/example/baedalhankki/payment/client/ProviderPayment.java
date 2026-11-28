package com.example.baedalhankki.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderPayment(
        String paymentKey, String orderId, Integer totalAmount, Integer balanceAmount,
        String currency, String status, List<ProviderCancel> cancels) {
    public ProviderPayment(String paymentKey, String orderId, Integer totalAmount, Integer balanceAmount,
            String currency, String status) {
        this(paymentKey, orderId, totalAmount, balanceAmount, currency, status, List.of());
    }

    public String lastCancelTransactionKey() {
        return cancels == null || cancels.isEmpty() ? null : cancels.getLast().transactionKey();
    }
}
