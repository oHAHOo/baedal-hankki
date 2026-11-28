package com.example.baedalhankki.refund.facade;

import com.example.baedalhankki.refund.service.PaymentRecoveryStore;
import com.example.baedalhankki.refund.service.WebhookInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.refund.enabled", havingValue = "true", matchIfMissing = true)
public class RefundRecoveryWorker {
    private final WebhookInboxService inbox;
    private final PaymentRecoveryFacade recovery;
    private final PaymentRecoveryStore store;

    @Scheduled(fixedDelayString = "${app.refund.poll-interval:60s}")
    public void recover() {
        for (var event : inbox.pending()) {
            try {
                inbox.complete(event.id(), event.orderId() != null && recovery.reconcile(event.orderId()));
            } catch (RuntimeException exception) {
                log.warn("Webhook recovery deferred for inbox {}", event.id());
            }
        }
        for (Long orderId : store.dueOrderIds()) {
            try {
                recovery.reconcile(orderId);
            } catch (RuntimeException exception) {
                log.warn("Scheduled payment recovery deferred for order {}", orderId);
            }
        }
        for (Long orderId : store.expiredUnpreparedOrderIds()) {
            try {
                store.expireUnprepared(orderId);
            } catch (RuntimeException exception) {
                log.warn("Unpaid order expiry deferred for order {}", orderId);
            }
        }
    }
}
