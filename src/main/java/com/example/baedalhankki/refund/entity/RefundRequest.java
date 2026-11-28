package com.example.baedalhankki.refund.entity;

import com.example.baedalhankki.global.entity.BaseEntity;
import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long orderId;
    @Column(nullable = false)
    private Long requestedBy;
    @Column(nullable = false, length = 20)
    private String actorRole;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private int amount;
    @Enumerated(EnumType.STRING)
    private OrderStatus previousOrderStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;
    private Instant requestedAt;
    private Instant resolvedAt;
    private Long decidedBy;
    @Column(length = 500)
    private String decisionNote;
    @Column(length = 200)
    private String providerTransactionKey;

    public static RefundRequest create(Order order, Long actorId, String role, String reason,
            boolean approved, Instant now) {
        RefundRequest request = new RefundRequest();
        request.orderId = order.getId();
        request.requestedBy = actorId;
        request.actorRole = role;
        request.reason = reason;
        request.amount = order.getTotalAmount();
        request.previousOrderStatus = order.getStatus();
        request.status = approved ? RefundStatus.PROCESSING : RefundStatus.REQUESTED;
        request.requestedAt = now;
        return request;
    }

    public void decide(Long actorId, String note, boolean approve) {
        if (status != RefundStatus.REQUESTED && status != RefundStatus.MANUAL_REVIEW) {
            throw new ApiException(ErrorCode.REFUND_STATE_CONFLICT);
        }
        decidedBy = actorId;
        decisionNote = note;
        status = approve ? RefundStatus.PROCESSING : RefundStatus.REJECTED;
    }

    public void complete(String transactionKey, Instant now) {
        status = RefundStatus.COMPLETED;
        if (transactionKey != null) {
            providerTransactionKey = transactionKey;
        }
        if (resolvedAt == null) {
            resolvedAt = now;
        }
    }

    public void review() {
        if (status != RefundStatus.COMPLETED) {
            status = RefundStatus.MANUAL_REVIEW;
        }
    }
}
