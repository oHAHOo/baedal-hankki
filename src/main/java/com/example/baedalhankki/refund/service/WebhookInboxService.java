package com.example.baedalhankki.refund.service;

import com.example.baedalhankki.refund.config.RefundSettings;
import com.example.baedalhankki.refund.repository.WebhookInboxRepository;
import com.example.baedalhankki.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookInboxService {
    private final WebhookInboxRepository inbox;
    private final PaymentRepository payments;
    private final RefundSettings settings;
    private final Clock clock;

    public record PendingWebhook(Long id, Long orderId) {
    }

    public List<PendingWebhook> pending() {
        return inbox.findByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                clock.instant(), PageRequest.of(0, settings.batchSize())).stream()
                .map(event -> new PendingWebhook(event.getId(), payments.findByProviderOrderId(event.getProviderOrderId())
                        .map(payment -> payment.getOrderId()).orElse(null))).toList();
    }

    @Transactional
    public void complete(Long id, boolean success) {
        var event = inbox.findForUpdate(id).orElseThrow();
        if (event.getProcessedAt() != null) {
            return;
        }
        if (success) {
            event.processed(clock.instant());
        } else {
            event.retry(clock.instant(), "RECONCILIATION_PENDING");
        }
    }
}
