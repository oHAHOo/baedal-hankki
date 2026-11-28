package com.example.baedalhankki.refund.dto;

import com.example.baedalhankki.refund.entity.RefundRequest;
import com.example.baedalhankki.refund.entity.RefundStatus;
import java.time.Instant;

public record RefundResponseDto(Long refundId, Long orderId, int amount, RefundStatus status,
        String reason, Instant requestedAt, Instant resolvedAt) {
    public static RefundResponseDto from(RefundRequest request) {
        return new RefundResponseDto(request.getId(), request.getOrderId(), request.getAmount(),
                request.getStatus(), request.getReason(), request.getRequestedAt(), request.getResolvedAt());
    }
}
