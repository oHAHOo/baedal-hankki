package com.example.baedalhankki.refund.service;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.order.repository.OrderRepository;
import com.example.baedalhankki.payment.client.ProviderPayment;
import com.example.baedalhankki.payment.entity.Payment;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import com.example.baedalhankki.payment.repository.PaymentRepository;
import com.example.baedalhankki.refund.config.RefundSettings;
import com.example.baedalhankki.refund.dto.RecoveryTarget;
import com.example.baedalhankki.refund.entity.RefundAlert;
import com.example.baedalhankki.refund.entity.RefundRequest;
import com.example.baedalhankki.refund.entity.RefundStatus;
import com.example.baedalhankki.refund.repository.RefundAlertRepository;
import com.example.baedalhankki.refund.repository.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRecoveryStore {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRequestRepository refunds;
    private final RefundAlertRepository alerts;
    private final RefundSettings settings;
    private final Clock clock;

    public List<Long> dueOrderIds() {
        return payments.findDueOrderIds(clock.instant(), PageRequest.of(0, settings.batchSize()));
    }

    public List<Long> expiredUnpreparedOrderIds() {
        var cutoff = java.time.LocalDateTime.ofInstant(clock.instant().minus(settings.unpaidExpiry()),
                java.time.ZoneOffset.UTC);
        return orders.findUnpreparedExpired(OrderStatus.WAITING_PAYMENT, cutoff,
                PageRequest.of(0, settings.batchSize()));
    }

    @Transactional
    public void expireUnprepared(Long orderId) {
        Order order = orders.findForUpdate(orderId).orElseThrow();
        if (order.getStatus() == OrderStatus.WAITING_PAYMENT && payments.findByOrderId(orderId).isEmpty()) {
            var refund = refunds.save(RefundRequest.create(order, order.getCustomerId(), "SYSTEM",
                    "미결제 주문 유효시간 만료", true, clock.instant()));
            order.beginAuthorizedCancellation();
            order.finishAuthorizedCancellation();
            refund.complete(null, clock.instant());
        }
    }

    @Transactional
    public RecoveryTarget claim(Long orderId) {
        orders.findForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = findPayment(orderId);
        String token = UUID.randomUUID().toString();
        if (!payment.claim(token, clock.instant(), settings.leaseDuration())) {
            return null;
        }
        return RecoveryTarget.of(payment, token);
    }

    @Transactional
    public boolean apply(RecoveryTarget target, ProviderPayment result) {
        Order order = orders.findForUpdate(target.orderId()).orElseThrow();
        Payment payment = findPayment(target.orderId());
        if (!payment.ownsLease(target.token())) {
            return false;
        }
        Instant now = clock.instant();
        if (result == null) {
            if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.CANCELING) {
                order.hold();
                raise(order.getId(), "PROVIDER_PAYMENT_NOT_FOUND", now);
            }
            if (payment.getStatus() == PaymentStatus.READY && payment.getPreparedAt() != null
                    && !now.isBefore(payment.getPreparedAt().plus(settings.unpaidExpiry()))) {
                var refund = refunds.save(RefundRequest.create(order, order.getCustomerId(), "SYSTEM",
                        "미결제 주문 유효시간 만료", true, now));
                order.beginAuthorizedCancellation();
                order.finishAuthorizedCancellation();
                payment.observeCanceled();
                refund.complete(null, now);
            }
            return false;
        }
        validate(payment, result);
        if ("CANCELED".equals(result.status()) && Integer.valueOf(0).equals(result.balanceAmount())) {
            boolean external = payment.getStatus() != PaymentStatus.CANCELING;
            if (payment.getStatus() != PaymentStatus.CANCELED) {
                if (external && order.getStatus() != OrderStatus.CANCELING && order.getStatus() != OrderStatus.CANCELED) {
                    refunds.save(RefundRequest.create(order, order.getCustomerId(), "SYSTEM", "결제사 외부 취소 감지",
                            true, now));
                    if (order.getStatus() == OrderStatus.COOKING || order.getStatus() == OrderStatus.DELIVERING
                            || order.getStatus() == OrderStatus.COMPLETED) {
                        order.hold();
                        raise(order.getId(), "EXTERNAL_REFUND_REVIEW", now);
                    } else {
                        order.beginAuthorizedCancellation();
                        order.finishAuthorizedCancellation();
                    }
                } else if (order.getStatus() == OrderStatus.CANCELING) {
                    order.finishAuthorizedCancellation();
                }
                payment.observeCanceled();
                completeRefund(order.getId(), result.lastCancelTransactionKey(), now);
            }
            return true;
        }
        if ("PARTIAL_CANCELED".equals(result.status())) {
            order.hold();
            raise(order.getId(), "PARTIAL_REFUND_REVIEW", now);
            return true;
        }
        if ("DONE".equals(result.status()) && Integer.valueOf(payment.getAmount()).equals(result.balanceAmount())) {
            if (payment.getStatus() == PaymentStatus.CANCELED || payment.getStatus() == PaymentStatus.FAILED) {
                order.hold();
                raise(order.getId(), "PAYMENT_STATE_MISMATCH", now);
                return false;
            }
            boolean startingCancellation = payment.getStatus() != PaymentStatus.CANCELING;
            payment.observePaymentKey(result.paymentKey());
            payment.observePaid();
            if (payment.isCancellationRequested() || order.getStatus() == OrderStatus.CANCELING) {
                payment.beginCancellation();
                if (startingCancellation) {
                    payment.trackOperation(now);
                }
            } else if (order.getStatus() == OrderStatus.WAITING_PAYMENT) {
                order.markPaid();
            } else if (order.getStatus() == OrderStatus.CANCELED) {
                order.hold();
                raise(order.getId(), "PAYMENT_ON_CANCELED_ORDER", now);
            }
            return true;
        }
        if ("ABORTED".equals(result.status()) || "EXPIRED".equals(result.status())) {
            if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.CANCELING) {
                raise(order.getId(), "PAYMENT_STATE_MISMATCH", now);
                return false;
            }
            payment.observeFailed();
            if (payment.isCancellationRequested()) {
                payment.observeCanceled();
                order.finishAuthorizedCancellation();
                completeRefund(order.getId(), null, now);
            } else if (payment.getPreparedAt() != null
                    && !now.isBefore(payment.getPreparedAt().plus(settings.unpaidExpiry()))) {
                order.beginAuthorizedCancellation();
                order.finishAuthorizedCancellation();
                payment.observeCanceled();
            }
            return true;
        }
        return false;
    }

    @Transactional
    public void finish(RecoveryTarget target, String error) {
        orders.findForUpdate(target.orderId()).orElseThrow();
        Payment payment = findPayment(target.orderId());
        if (!payment.ownsLease(target.token())) {
            return;
        }
        Instant now = clock.instant();
        boolean unresolved = payment.getStatus() == PaymentStatus.CONFIRMING
                || payment.getStatus() == PaymentStatus.CANCELING;
        if (unresolved && payment.getOperationStartedAt() == null) {
            raise(target.orderId(), "LEGACY_PENDING_PAYMENT_REVIEW", now);
        }
        if (unresolved && payment.getOperationStartedAt() != null
                && !now.isBefore(payment.getOperationStartedAt().plus(settings.alertAfter()))) {
            raise(target.orderId(), "LONG_PENDING_PAYMENT", now);
        }
        if (error != null && !unresolved) {
            raise(target.orderId(), "PAYMENT_RECONCILIATION_ERROR", now);
        }
        long delay = settings.recoveryDelay().toMillis();
        if (error != null) {
            delay = Math.min(3_600_000L, Math.max(1000L, delay) * (1L << Math.min(6, payment.getRecoveryAttempts())));
            delay += ThreadLocalRandom.current().nextLong(Math.max(1, delay / 5));
        }
        // 완료된 결제도 외부 관리자 화면의 변경을 탐지하도록 낮은 빈도로 조회한다.
        if (payment.getStatus() == PaymentStatus.CANCELED || payment.getStatus() == PaymentStatus.FAILED) {
            delay = Math.max(delay, 86_400_000L);
        }
        payment.checked(now, now.plusMillis(delay), error);
    }

    public RecoveryTarget read(Long orderId) {
        return RecoveryTarget.of(findPayment(orderId), null);
    }

    public void checkRetryWindow(Long orderId, boolean cancellation) {
        Payment payment = findPayment(orderId);
        if (!payment.canRetry(clock.instant(), settings.automaticRetryLimit(), cancellation)) {
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }
    }

    private void validate(Payment payment, ProviderPayment result) {
        if (!payment.getProviderOrderId().equals(result.orderId()) || result.paymentKey() == null
                || result.paymentKey().isBlank() || result.paymentKey().length() > 200
                || payment.getPaymentKey() != null && !payment.getPaymentKey().equals(result.paymentKey())
                || !Integer.valueOf(payment.getAmount()).equals(result.totalAmount())
                || !"KRW".equals(result.currency())) {
            throw new ApiException(ErrorCode.PAYMENT_RETRY_REQUIRED);
        }
    }

    private Payment findPayment(Long orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void raise(Long orderId, String code, Instant now) {
        if (!alerts.existsByOrderIdAndCode(orderId, code)) {
            alerts.save(RefundAlert.create(orderId, code, now));
        }
    }

    private void completeRefund(Long orderId, String key, Instant now) {
        refunds.findFirstByOrderIdOrderByIdDesc(orderId)
                .filter(refund -> refund.getStatus() == RefundStatus.PROCESSING)
                .ifPresent(refund -> refund.complete(key, now));
    }
}
