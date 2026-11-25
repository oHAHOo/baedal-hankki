package com.example.baedalhankki.payment.facade;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.order.dto.OrderStateResponseDto;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.payment.client.PaymentGateway;
import com.example.baedalhankki.payment.client.ProviderPayment;
import com.example.baedalhankki.payment.dto.PaymentConfirmRequestDto;
import com.example.baedalhankki.payment.dto.PaymentOperation;
import com.example.baedalhankki.payment.dto.PaymentPrepareResponseDto;
import com.example.baedalhankki.payment.dto.PaymentResponseDto;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import com.example.baedalhankki.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentService payments;
    private final PaymentGateway gateway;

    public PaymentPrepareResponseDto prepare(Long orderId, Long customerId) {
        return payments.prepare(orderId, customerId, gateway.getClientKey());
    }

    public PaymentResponseDto confirm(Long orderId, Long customerId, PaymentConfirmRequestDto request) {
        gateway.checkAvailable();
        PaymentOperation operation = payments.beginConfirmation(orderId, customerId, request);
        if (operation.payment().status() == PaymentStatus.PAID) {
            return operation.payment();
        }
        ProviderPayment result = gateway.find(operation.payment().providerOrderId()).orElse(null);
        if (result != null) {
            validateIdentity(operation, result);
        }
        if (result == null || "READY".equals(result.status()) || "IN_PROGRESS".equals(result.status())) {
            result = gateway.confirm(operation.paymentKey(), operation.payment().providerOrderId(),
                    operation.payment().amount(), "confirm_" + operation.payment().providerOrderId());
        }
        validateIdentity(operation, result);
        if ("ABORTED".equals(result.status()) || "EXPIRED".equals(result.status())
                || "CANCELED".equals(result.status())) {
            payments.failConfirmation(orderId, customerId);
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        if (!"DONE".equals(result.status()) || result.balanceAmount() == null
                || result.balanceAmount() != operation.payment().amount()) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        return payments.finishConfirmation(orderId, customerId);
    }

    public OrderStateResponseDto cancelOrder(Long orderId, Long customerId) {
        PaymentOperation operation = payments.beginCancellation(orderId, customerId);
        if (operation.order().status() == OrderStatus.CANCELED) {
            return operation.order();
        }
        gateway.checkAvailable();
        ProviderPayment result = gateway.find(operation.payment().providerOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED));
        validateIdentity(operation, result);
        if ("DONE".equals(result.status())) {
            result = gateway.cancel(operation.paymentKey(), "cancel_" + operation.payment().providerOrderId());
        }
        validateIdentity(operation, result);
        if (!"CANCELED".equals(result.status()) || result.balanceAmount() == null || result.balanceAmount() != 0) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        return payments.finishCancellation(orderId, customerId);
    }

    private void validateIdentity(PaymentOperation operation, ProviderPayment result) {
        if (result == null || !operation.payment().providerOrderId().equals(result.orderId())
                || !operation.paymentKey().equals(result.paymentKey()) || result.totalAmount() == null
                || result.totalAmount() != operation.payment().amount() || !"KRW".equals(result.currency())) {
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }
    }
}
