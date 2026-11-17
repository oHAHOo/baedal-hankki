package com.example.baedalhankki.order.controller;

import lombok.RequiredArgsConstructor;
import com.example.baedalhankki.global.security.AuthMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.example.baedalhankki.order.dto.OrderDetailResponseDto;
import com.example.baedalhankki.order.dto.OwnerOrderListResponseDto;
import com.example.baedalhankki.order.dto.OrderStateResponseDto;
import com.example.baedalhankki.order.service.OrderService;

import org.springframework.http.ResponseEntity;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/orders")
@RequiredArgsConstructor
public class OwnerOrderController {
    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<OwnerOrderListResponseDto> getOrders(@AuthenticationPrincipal AuthMember authMember) {
        return ResponseEntity.ok(orderService.getOwnerOrders(authMember.memberId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponseDto> getOrder(
            @AuthenticationPrincipal AuthMember authMember, @PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.getOwnerOrder(id, authMember.memberId()));
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<OrderStateResponseDto> acceptOrder(
            @AuthenticationPrincipal AuthMember authMember, @PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.acceptOrder(id, authMember.memberId()));
    }

    @PatchMapping("/{id}/dispatch")
    public ResponseEntity<OrderStateResponseDto> dispatchOrder(
            @AuthenticationPrincipal AuthMember authMember, @PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.dispatchOrder(id, authMember.memberId()));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<OrderStateResponseDto> completeOrder(
            @AuthenticationPrincipal AuthMember authMember, @PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.completeOrder(id, authMember.memberId()));
    }
}
