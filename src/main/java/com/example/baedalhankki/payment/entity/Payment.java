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

    public static Payment create(Long orderId, int amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.providerOrderId = UUID.randomUUID().toString();
        payment.amount = amount;
        payment.status = PaymentStatus.READY;
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
}
