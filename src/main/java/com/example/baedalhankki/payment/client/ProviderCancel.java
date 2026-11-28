package com.example.baedalhankki.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderCancel(String transactionKey) {
}
