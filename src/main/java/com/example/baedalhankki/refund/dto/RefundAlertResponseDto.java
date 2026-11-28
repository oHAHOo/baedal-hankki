package com.example.baedalhankki.refund.dto;

import com.example.baedalhankki.refund.entity.RefundAlert;
import java.time.Instant;

public record RefundAlertResponseDto(Long alertId, Long orderId, String code, Instant raisedAt,
        Instant acknowledgedAt) {
    public static RefundAlertResponseDto from(RefundAlert alert) {
        return new RefundAlertResponseDto(alert.getId(), alert.getOrderId(), alert.getCode(),
                alert.getRaisedAt(), alert.getAcknowledgedAt());
    }
}
