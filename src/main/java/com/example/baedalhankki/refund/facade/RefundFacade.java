package com.example.baedalhankki.refund.facade;

import com.example.baedalhankki.refund.dto.RefundResponseDto;
import com.example.baedalhankki.refund.entity.RefundStatus;
import com.example.baedalhankki.refund.service.RefundService;
import com.example.baedalhankki.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundFacade {
    private final RefundService refunds;
    private final PaymentRecoveryFacade recovery;
    private final PaymentRepository payments;

    public RefundResponseDto request(Long orderId, Long actorId, boolean owner, String reason) {
        return recover(refunds.request(orderId, actorId, owner, reason));
    }

    public RefundResponseDto decide(Long id, Long adminId, String note, boolean approved) {
        return recover(refunds.decide(id, adminId, note, approved));
    }

    public RefundResponseDto retry(Long id) {
        return recover(refunds.get(id));
    }

    private RefundResponseDto recover(RefundResponseDto response) {
        if (response.status() == RefundStatus.PROCESSING && payments.findByOrderId(response.orderId()).isPresent()) {
            recovery.reconcile(response.orderId());
        }
        return refunds.get(response.refundId());
    }
}
