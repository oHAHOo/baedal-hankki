package com.example.baedalhankki.payment.entity;

import com.example.baedalhankki.global.entity.BaseEntity;
import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "provider_order_id", nullable = false, unique = true, length = 64)
    private String providerOrderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean cancellationRequested;

    private Instant preparedAt;
    private Instant operationStartedAt;
    private Instant confirmationStartedAt;
    private Instant cancellationStartedAt;
    private Instant nextCheckAt;
    private Instant leaseUntil;
    private String leaseToken;
    private Instant lastCheckedAt;
    private String lastErrorCode;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int recoveryAttempts;

    public static Payment create(Long orderId, int amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.providerOrderId = UUID.randomUUID().toString();
        payment.amount = amount;
        payment.status = PaymentStatus.READY;
        payment.preparedAt = Instant.now();
        payment.nextCheckAt = payment.preparedAt;
        return payment;
    }

    public void beginConfirmation(String key) {
        if (status != PaymentStatus.READY && status != PaymentStatus.CONFIRMING && status != PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        if (paymentKey != null && !paymentKey.equals(key)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        paymentKey = key;
        if (status == PaymentStatus.READY) {
            status = PaymentStatus.CONFIRMING;
        }
    }

    public void approve() {
        if (status != PaymentStatus.CONFIRMING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        status = PaymentStatus.PAID;
    }

    public void beginCancellation() {
        if (status == PaymentStatus.READY || status == PaymentStatus.FAILED) {
            status = PaymentStatus.CANCELED;
        } else if (status == PaymentStatus.PAID) {
            status = PaymentStatus.CANCELING;
        } else if (status != PaymentStatus.CANCELING && status != PaymentStatus.CANCELED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
    }

    public void cancel() {
        if (status != PaymentStatus.CANCELING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        status = PaymentStatus.CANCELED;
    }

    public void fail() {
        if (status != PaymentStatus.CONFIRMING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        status = PaymentStatus.FAILED;
    }

    public void requestCancellation() {
        cancellationRequested = true;
    }

    public void trackOperation(Instant now) {
        if (status == PaymentStatus.CONFIRMING && confirmationStartedAt == null) {
            confirmationStartedAt = now;
            operationStartedAt = now;
        }
        if (status == PaymentStatus.CANCELING && cancellationStartedAt == null) {
            cancellationStartedAt = now;
            operationStartedAt = now;
        }
        nextCheckAt = now;
    }

    public boolean canRetry(Instant now, java.time.Duration limit, boolean cancellation) {
        Instant started = cancellation ? cancellationStartedAt : confirmationStartedAt;
        return started != null && now.isBefore(started.plus(limit));
    }

    public void observePaymentKey(String key) {
        if (paymentKey != null && !paymentKey.equals(key)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        paymentKey = key;
    }

    public void observePaid() {
        if (status == PaymentStatus.READY || status == PaymentStatus.CONFIRMING) {
            status = PaymentStatus.PAID;
        }
    }

    public void observeCanceled() {
        status = PaymentStatus.CANCELED;
    }

    public void observeFailed() {
        if (status == PaymentStatus.READY || status == PaymentStatus.CONFIRMING) {
            status = PaymentStatus.FAILED;
        }
    }

    public boolean claim(String token, Instant now, java.time.Duration lease) {
        if (leaseUntil != null && now.isBefore(leaseUntil)) {
            return false;
        }
        leaseToken = token;
        leaseUntil = now.plus(lease);
        return true;
    }

    public boolean ownsLease(String token) {
        return token != null && token.equals(leaseToken);
    }

    public void checked(Instant now, Instant next, String error) {
        lastCheckedAt = now;
        nextCheckAt = next;
        lastErrorCode = error;
        recoveryAttempts = error == null ? 0 : recoveryAttempts + 1;
        leaseToken = null;
        leaseUntil = null;
    }

    public void wakeUp(Instant now) {
        nextCheckAt = now;
    }
}
