package com.example.baedalhankki.refund.service;

import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.order.entity.Order;
import com.example.baedalhankki.order.entity.OrderStatus;
import com.example.baedalhankki.order.repository.OrderRepository;
import com.example.baedalhankki.payment.entity.PaymentStatus;
import com.example.baedalhankki.payment.repository.PaymentRepository;
import com.example.baedalhankki.refund.dto.RefundResponseDto;
import com.example.baedalhankki.refund.dto.RefundAlertResponseDto;
import com.example.baedalhankki.refund.entity.RefundRequest;
import com.example.baedalhankki.refund.entity.RefundStatus;
import com.example.baedalhankki.refund.repository.RefundRequestRepository;
import com.example.baedalhankki.refund.repository.RefundAlertRepository;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRequestRepository refunds;
    private final RefundAlertRepository alerts;
    private final RestaurantRepository restaurants;
    private final Clock clock;

    @Transactional
    public RefundResponseDto request(Long orderId, Long actorId, boolean owner, String reason) {
        Order order = orders.findForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        authorize(order, actorId, owner);
        var latest = refunds.findFirstByOrderIdOrderByIdDesc(orderId);
        if (latest.isPresent() && latest.get().getStatus() != RefundStatus.REJECTED) {
            if (owner && latest.get().getStatus() == RefundStatus.REQUESTED) {
                latest.get().decide(actorId, reason, true);
                start(order, latest.get());
            }
            return RefundResponseDto.from(latest.get());
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new ApiException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        boolean approved = owner || order.getStatus() == OrderStatus.WAITING_PAYMENT
                || order.getStatus() == OrderStatus.PENDING;
        RefundRequest refund = refunds.save(RefundRequest.create(order, actorId,
                owner ? "OWNER" : "CUSTOMER", reason, approved, clock.instant()));
        if (approved) {
            start(order, refund);
        }
        return RefundResponseDto.from(refund);
    }

    @Transactional
    public RefundResponseDto decide(Long id, Long adminId, String note, boolean approved) {
        Long orderId = refunds.findOrderId(id).orElseThrow(() -> new ApiException(ErrorCode.REFUND_NOT_FOUND));
        Order order = orders.findForUpdate(orderId).orElseThrow();
        RefundRequest refund = refunds.findById(id).orElseThrow();
        refund.decide(adminId, note, approved);
        if (approved) {
            start(order, refund);
        }
        return RefundResponseDto.from(refund);
    }

    public List<RefundResponseDto> getOrderRefunds(Long orderId, Long actorId, boolean owner) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        authorize(order, actorId, owner);
        return refunds.findByOrderIdOrderByIdAsc(orderId).stream().map(RefundResponseDto::from).toList();
    }

    public RefundResponseDto get(Long id) {
        return RefundResponseDto.from(refunds.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.REFUND_NOT_FOUND)));
    }

    public List<RefundResponseDto> pending() {
        return refunds.findByStatusInOrderByRequestedAtAsc(List.of(RefundStatus.REQUESTED,
                RefundStatus.PROCESSING, RefundStatus.MANUAL_REVIEW), PageRequest.of(0, 100))
                .stream().map(RefundResponseDto::from).toList();
    }

    public List<RefundAlertResponseDto> getAlerts(Long ownerId) {
        var page = PageRequest.of(0, 100);
        return (ownerId == null ? alerts.findByAcknowledgedAtIsNullOrderByRaisedAtAsc(page)
                : alerts.findOwnerAlerts(ownerId, page)).stream().map(RefundAlertResponseDto::from).toList();
    }

    @Transactional
    public void acknowledge(Long alertId, Long adminId, String note) {
        var alert = alerts.findById(alertId).orElseThrow(() -> new ApiException(ErrorCode.REFUND_NOT_FOUND));
        alert.acknowledge(adminId, note, clock.instant());
    }

    @Transactional
    public RefundResponseDto confirmCashReturn(Long id, Long adminId, String evidence) {
        Long orderId = refunds.findOrderId(id).orElseThrow(() -> new ApiException(ErrorCode.REFUND_NOT_FOUND));
        Order order = orders.findForUpdate(orderId).orElseThrow();
        RefundRequest refund = refunds.findById(id).orElseThrow();
        if (order.isOnlinePayment() || refund.getStatus() != RefundStatus.MANUAL_REVIEW) {
            throw new ApiException(ErrorCode.REFUND_STATE_CONFLICT);
        }
        refund.decide(adminId, evidence, true);
        refund.complete(null, clock.instant());
        return RefundResponseDto.from(refund);
    }

    private void start(Order order, RefundRequest refund) {
        order.beginAuthorizedCancellation();
        if (!order.isOnlinePayment()) {
            order.finishAuthorizedCancellation();
            // 현장 수납 여부는 PG로 확인할 수 없으므로 운영 확인 대상으로 남긴다.
            refund.review();
            return;
        }
        var payment = payments.findByOrderId(order.getId()).orElse(null);
        if (payment == null) {
            order.finishAuthorizedCancellation();
            refund.complete(null, clock.instant());
            return;
        }
        payment.requestCancellation();
        boolean starting = payment.getStatus() == PaymentStatus.PAID;
        if (payment.getStatus() != PaymentStatus.CONFIRMING) {
            payment.beginCancellation();
        }
        if (starting) {
            payment.trackOperation(clock.instant());
        } else {
            payment.wakeUp(clock.instant());
        }
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            order.finishAuthorizedCancellation();
            refund.complete(null, clock.instant());
        }
    }

    private void authorize(Order order, Long actorId, boolean owner) {
        boolean allowed = owner ? restaurants.findById(order.getRestaurantId())
                .map(restaurant -> actorId.equals(restaurant.getOwnerId())).orElse(false)
                : actorId.equals(order.getCustomerId());
        if (!allowed) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
