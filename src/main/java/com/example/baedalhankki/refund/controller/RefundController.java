package com.example.baedalhankki.refund.controller;

import com.example.baedalhankki.global.security.AuthMember;
import com.example.baedalhankki.refund.dto.RefundReasonRequestDto;
import com.example.baedalhankki.refund.dto.RefundResponseDto;
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
public class RefundController {
    private final RefundFacade facade;
    private final RefundService service;

    @PostMapping("/api/orders/{orderId}/refunds")
    public ResponseEntity<RefundResponseDto> request(@AuthenticationPrincipal AuthMember member,
            @PathVariable @Positive Long orderId, @Valid @RequestBody RefundReasonRequestDto request) {
        return ResponseEntity.accepted().body(facade.request(orderId, member.memberId(), false, request.reason()));
    }

    @GetMapping("/api/orders/{orderId}/refunds")
    public ResponseEntity<List<RefundResponseDto>> list(@AuthenticationPrincipal AuthMember member,
            @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(service.getOrderRefunds(orderId, member.memberId(), false));
    }

    @PostMapping("/api/owner/orders/{orderId}/refunds")
    public ResponseEntity<RefundResponseDto> ownerRequest(@AuthenticationPrincipal AuthMember member,
            @PathVariable @Positive Long orderId, @Valid @RequestBody RefundReasonRequestDto request) {
        return ResponseEntity.accepted().body(facade.request(orderId, member.memberId(), true, request.reason()));
    }

    @GetMapping("/api/owner/orders/{orderId}/refunds")
    public ResponseEntity<List<RefundResponseDto>> ownerList(@AuthenticationPrincipal AuthMember member,
            @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(service.getOrderRefunds(orderId, member.memberId(), true));
    }
}
