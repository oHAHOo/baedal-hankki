package com.example.baedalhankki.refund.facade;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.payment.client.PaymentGateway;
import com.example.baedalhankki.payment.client.ProviderPayment;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import com.example.baedalhankki.refund.dto.RecoveryTarget;
import com.example.baedalhankki.refund.service.PaymentRecoveryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryFacade {
    private final PaymentGateway gateway;
    private final PaymentRecoveryStore store;

    public boolean reconcile(Long orderId) {
        RecoveryTarget target = store.claim(orderId);
        if (target == null) {
            return false;
        }
        String error = null;
        try {
            ProviderPayment result = gateway.find(target.providerOrderId()).orElse(null);
            store.apply(target, result);
            RecoveryTarget current = store.read(orderId);
            if (current.status() == PaymentStatus.CONFIRMING
                    && (result == null || "READY".equals(result.status()) || "IN_PROGRESS".equals(result.status()))) {
                store.checkRetryWindow(orderId, false);
                result = gateway.confirm(current.paymentKey(), current.providerOrderId(), current.amount(),
                        "confirm_" + current.providerOrderId());
                store.apply(target, result);
                current = store.read(orderId);
            }
            if (current.status() == PaymentStatus.CANCELING && result != null && "DONE".equals(result.status())) {
                store.checkRetryWindow(orderId, true);
                result = gateway.cancel(current.paymentKey(), "cancel_" + current.providerOrderId());
                store.apply(target, result);
            }
            return true;
        } catch (ApiException exception) {
            error = exception.getErrorCode().name();
            return false;
        } catch (RuntimeException exception) {
            error = "RECOVERY_ERROR";
            log.warn("Payment recovery failed for order {}", orderId);
            return false;
        } finally {
            store.finish(target, error);
        }
    }
}
