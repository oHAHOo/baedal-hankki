package com.example.baedalhankki.refund.service;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.refund.config.RefundSettings;
import com.example.baedalhankki.refund.entity.WebhookInbox;
import com.example.baedalhankki.refund.repository.WebhookInboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TossWebhookService {
    private final RefundSettings settings;
    private final Clock clock;
    private final ObjectMapper json;
    private final WebhookInboxRepository inbox;
    private final PlatformTransactionManager transactionManager;

    public void receive(byte[] raw, String timestamp, String signatures) {
        verify(raw, timestamp, signatures);
        String hash;
        String providerOrderId;
        try {
            var event = json.readTree(raw);
            if (!"PAYMENT_STATUS_CHANGED".equals(event.path("eventType").asText())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST);
            }
            providerOrderId = event.path("data").path("orderId").asText();
            if (providerOrderId.length() < 6 || providerOrderId.length() > 64) {
                throw new ApiException(ErrorCode.INVALID_REQUEST);
            }
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        // 중복 삽입 실패는 롤백 완료 후 확인한다. 저장 실패에는 200을 보내지 않는다.
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (!inbox.existsByPayloadHash(hash)) {
                    inbox.saveAndFlush(WebhookInbox.create(hash, providerOrderId, clock.instant()));
                }
            });
        } catch (DataIntegrityViolationException exception) {
            if (!inbox.existsByPayloadHash(hash)) {
                throw exception;
            }
        }
    }

    private void verify(byte[] raw, String timestamp, String signatures) {
        if (settings.webhookSecurityKey().isBlank()) {
            throw new ApiException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        try {
            if (raw.length > 131072 || timestamp == null || signatures == null || signatures.length() > 8192
                    || Duration.between(Instant.parse(timestamp), clock.instant()).abs().compareTo(Duration.ofMinutes(5)) > 0) {
                throw new ApiException(ErrorCode.INVALID_WEBHOOK);
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.webhookSecurityKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(raw);
            byte[] expected = mac.doFinal((":" + timestamp).getBytes(StandardCharsets.UTF_8));
            for (String signature : signatures.split(",")) {
                String value = signature.trim();
                if (value.startsWith("v1:")) {
                    try {
                        if (MessageDigest.isEqual(expected, Base64.getDecoder().decode(value.substring(3)))) {
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // 서명 교체 기간의 다른 서명도 확인한다.
                    }
                }
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INVALID_WEBHOOK);
        }
        throw new ApiException(ErrorCode.INVALID_WEBHOOK);
    }
}
