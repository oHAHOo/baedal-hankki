package com.example.baedalhankki.refund.controller;

import com.example.baedalhankki.global.security.AuthMember;
import com.example.baedalhankki.refund.dto.RefundReasonRequestDto;
import com.example.baedalhankki.refund.dto.RefundResponseDto;
import com.example.baedalhankki.refund.dto.RefundAlertResponseDto;
import com.example.baedalhankki.refund.facade.RefundFacade;
import com.example.baedalhankki.refund.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RefundOperationsController {
    private final RefundService service;
    private final RefundFacade facade;

    @GetMapping("/api/admin/refunds")
    public ResponseEntity<List<RefundResponseDto>> pending() {
        return ResponseEntity.ok(service.pending());
    }

    @PostMapping("/api/admin/refunds/{id}/approve")
    public ResponseEntity<RefundResponseDto> approve(@AuthenticationPrincipal AuthMember admin,
            @PathVariable @Positive Long id, @Valid @RequestBody RefundReasonRequestDto request) {
        return ResponseEntity.accepted().body(facade.decide(id, admin.memberId(), request.reason(), true));
    }

    @PostMapping("/api/admin/refunds/{id}/reject")
    public ResponseEntity<RefundResponseDto> reject(@AuthenticationPrincipal AuthMember admin,
            @PathVariable @Positive Long id, @Valid @RequestBody RefundReasonRequestDto request) {
        return ResponseEntity.ok(facade.decide(id, admin.memberId(), request.reason(), false));
    }

    @PostMapping("/api/admin/refunds/{id}/reconcile")
    public ResponseEntity<RefundResponseDto> retry(@PathVariable @Positive Long id) {
        return ResponseEntity.accepted().body(facade.retry(id));
    }

    @PostMapping("/api/admin/refunds/{id}/confirm-cash-return")
    public ResponseEntity<RefundResponseDto> confirmCash(@AuthenticationPrincipal AuthMember admin,
            @PathVariable @Positive Long id, @Valid @RequestBody RefundReasonRequestDto request) {
        return ResponseEntity.ok(service.confirmCashReturn(id, admin.memberId(), request.reason()));
    }

    @GetMapping("/api/admin/refund-alerts")
    public ResponseEntity<List<RefundAlertResponseDto>> alerts() {
        return ResponseEntity.ok(service.getAlerts(null));
    }

    @GetMapping("/api/owner/refund-alerts")
    public ResponseEntity<List<RefundAlertResponseDto>> ownerAlerts(@AuthenticationPrincipal AuthMember member) {
        return ResponseEntity.ok(service.getAlerts(member.memberId()));
    }

    @PostMapping("/api/admin/refund-alerts/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@AuthenticationPrincipal AuthMember admin,
            @PathVariable @Positive Long id, @Valid @RequestBody RefundReasonRequestDto request) {
        service.acknowledge(id, admin.memberId(), request.reason());
        return ResponseEntity.ok().build();
    }
}
