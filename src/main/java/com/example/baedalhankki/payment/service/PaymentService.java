package com.example.baedalhankki.payment.service;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.order.dto.OrderStateResponseDto;
import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.order.repository.OrderRepository;
import com.example.baedalhankki.payment.dto.PaymentConfirmRequestDto;
import com.example.baedalhankki.payment.dto.PaymentOperation;
import com.example.baedalhankki.payment.dto.PaymentPrepareResponseDto;
import com.example.baedalhankki.payment.dto.PaymentResponseDto;
import com.example.baedalhankki.payment.entity.Payment;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import com.example.baedalhankki.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.baedalhankki.refund.entity.RefundRequest;
import com.example.baedalhankki.refund.entity.RefundStatus;
import com.example.baedalhankki.refund.repository.RefundRequestRepository;
import java.time.Clock;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRequestRepository refunds;
    private final Clock clock;

    @Transactional
    public PaymentPrepareResponseDto prepare(Long orderId, Long customerId, String clientKey) {
        Order order = findForUpdate(orderId, customerId);
        if (!order.isOnlinePayment() || order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        Payment payment = payments.findByOrderId(orderId)
                .orElseGet(() -> payments.save(Payment.create(orderId, order.getTotalAmount())));
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        return PaymentPrepareResponseDto.of(payment, clientKey);
    }

    public PaymentResponseDto getPayment(Long orderId, Long customerId) {
        orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return PaymentResponseDto.from(findPayment(orderId));
    }

    @Transactional
    public PaymentOperation beginConfirmation(Long orderId, Long customerId, PaymentConfirmRequestDto request) {
        Order order = findForUpdate(orderId, customerId);
        Payment payment = findPayment(orderId);
        if (!payment.getProviderOrderId().equals(request.orderId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if (request.amount() != payment.getAmount() || request.amount() != order.getTotalAmount()) {
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (payment.getStatus() != PaymentStatus.PAID && order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        boolean starting = payment.getStatus() == PaymentStatus.READY;
        payment.beginConfirmation(request.paymentKey());
        if (starting) {
            payment.trackOperation(clock.instant());
        } else {
            payment.wakeUp(clock.instant());
        }
        return snapshot(order, payment);
    }

    @Transactional
    public PaymentResponseDto finishConfirmation(Long orderId, Long customerId) {
        Order order = findForUpdate(orderId, customerId);
        Payment payment = findPayment(orderId);
        // 다른 동일 요청이 먼저 완료하거나 그 이후 취소한 상태를 되돌리지 않는다.
        if (payment.getStatus() == PaymentStatus.CONFIRMING) {
            payment.approve();
            if (payment.isCancellationRequested()) {
                payment.beginCancellation();
                payment.trackOperation(clock.instant());
            } else {
                order.markPaid();
            }
        }
        return PaymentResponseDto.from(payment);
    }

    @Transactional
    public void failConfirmation(Long orderId, Long customerId) {
        findForUpdate(orderId, customerId);
        Payment payment = findPayment(orderId);
        if (payment.getStatus() == PaymentStatus.CONFIRMING) {
            payment.fail();
        }
    }

    @Transactional
    public PaymentOperation beginCancellation(Long orderId, Long customerId) {
        Order order = findForUpdate(orderId, customerId);
        if (!order.isOnlinePayment()) {
            recordCancellation(order, customerId);
            order.cancel();
            completeRefund(orderId, null);
            return snapshot(order, null);
        }
        Payment payment = payments.findByOrderId(orderId).orElse(null);
        if (order.getStatus() == OrderStatus.CANCELED) {
            return snapshot(order, payment);
        }
        if (payment != null && payment.getStatus() == PaymentStatus.CONFIRMING) {
            if (!payment.isCancellationRequested()) {
                recordCancellation(order, customerId);
                order.beginOnlineCancellation();
                payment.requestCancellation();
            }
            payment.wakeUp(clock.instant());
            return snapshot(order, payment);
        }
        if (order.getStatus() != OrderStatus.CANCELING) {
            recordCancellation(order, customerId);
            order.beginOnlineCancellation();
        }
        if (payment != null) {
            boolean starting = payment.getStatus() == PaymentStatus.PAID;
            payment.beginCancellation();
            payment.requestCancellation();
            if (starting) {
                payment.trackOperation(clock.instant());
            } else {
                payment.wakeUp(clock.instant());
            }
        }
        if (payment == null || payment.getStatus() == PaymentStatus.CANCELED) {
            order.finishOnlineCancellation();
            completeRefund(orderId, null);
        }
        return snapshot(order, payment);
    }

    @Transactional
    public OrderStateResponseDto finishCancellation(Long orderId, Long customerId, String transactionKey) {
        Order order = findForUpdate(orderId, customerId);
        Payment payment = findPayment(orderId);
        if (payment.getStatus() == PaymentStatus.CANCELING) {
            payment.cancel();
            order.finishAuthorizedCancellation();
            completeRefund(orderId, transactionKey);
        }
        return OrderStateResponseDto.from(order);
    }

    private void recordCancellation(Order order, Long customerId) {
        refunds.save(RefundRequest.create(order, customerId, "CUSTOMER", "고객의 주문 취소", true, clock.instant()));
    }

    private void completeRefund(Long orderId, String transactionKey) {
        refunds.findFirstByOrderIdOrderByIdDesc(orderId)
                .filter(refund -> refund.getStatus() == RefundStatus.PROCESSING)
                .ifPresent(refund -> refund.complete(transactionKey, clock.instant()));
    }

    private Order findForUpdate(Long orderId, Long customerId) {
        // 승인·취소·사장님 접수가 같은 주문 잠금을 사용하고 항상 주문을 먼저 잠근다.
        return orders.findCustomerOrderForUpdate(orderId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Payment findPayment(Long orderId) {
        return payments.findByOrderId(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentOperation snapshot(Order order, Payment payment) {
        return new PaymentOperation(OrderStateResponseDto.from(order),
                payment == null ? null : PaymentResponseDto.from(payment),
                payment == null ? null : payment.getPaymentKey());
    }
}
