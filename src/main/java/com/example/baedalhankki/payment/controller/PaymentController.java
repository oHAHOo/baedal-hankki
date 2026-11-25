package com.example.baedalhankki.payment.controller;

import com.example.baedalhankki.global.security.AuthMember;
import com.example.baedalhankki.payment.dto.PaymentConfirmRequestDto;
import com.example.baedalhankki.payment.dto.PaymentPrepareResponseDto;
import com.example.baedalhankki.payment.dto.PaymentResponseDto;
import com.example.baedalhankki.payment.facade.PaymentFacade;
import com.example.baedalhankki.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentFacade facade;
    private final PaymentService payments;

    @PostMapping
    public ResponseEntity<PaymentPrepareResponseDto> prepare(
            @AuthenticationPrincipal AuthMember member, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(facade.prepare(orderId, member.memberId()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponseDto> confirm(
            @AuthenticationPrincipal AuthMember member, @PathVariable @Positive Long orderId,
            @Valid @RequestBody PaymentConfirmRequestDto request) {
        return ResponseEntity.ok(facade.confirm(orderId, member.memberId(), request));
    }

    @GetMapping
    public ResponseEntity<PaymentResponseDto> getPayment(
            @AuthenticationPrincipal AuthMember member, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(payments.getPayment(orderId, member.memberId()));
    }
}
