package com.example.baedalhankki.order.controller;

import lombok.RequiredArgsConstructor;

import com.example.baedalhankki.global.security.AuthMember;
import com.example.baedalhankki.order.dto.OrderCreateRequestDto;
import com.example.baedalhankki.order.dto.OrderDetailResponseDto;
import com.example.baedalhankki.order.dto.OrderListResponseDto;
import com.example.baedalhankki.order.dto.OrderStateResponseDto;
import com.example.baedalhankki.order.service.OrderService;
import com.example.baedalhankki.payment.facade.PaymentFacade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final PaymentFacade payments;

    @PostMapping
    public ResponseEntity<OrderStateResponseDto> createOrder(
            @AuthenticationPrincipal AuthMember authMember, @Valid @RequestBody OrderCreateRequestDto request,
            @RequestHeader("Idempotency-Key") String checkoutKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(authMember.memberId(), request, checkoutKey));
    }

    @GetMapping
    public ResponseEntity<OrderListResponseDto> getOrders(@AuthenticationPrincipal AuthMember authMember) {
        return ResponseEntity.ok(orderService.getOrders(authMember.memberId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponseDto> getOrder(
            @AuthenticationPrincipal AuthMember authMember, @PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.getOrder(id, authMember.memberId()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderStateResponseDto> cancelOrder(
            @AuthenticationPrincipal AuthMember authMember, @PathVariable @Positive Long id) {
        return ResponseEntity.ok(payments.cancelOrder(id, authMember.memberId()));
    }
}
