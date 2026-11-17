package com.example.baedalhankki.order.controller;

import lombok.RequiredArgsConstructor;

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
    public ResponseEntity<OwnerOrderListResponseDto> getOrders() {
        return ResponseEntity.ok(orderService.getOwnerOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponseDto> getOrder(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.getOwnerOrder(id));
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<OrderStateResponseDto> acceptOrder(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.acceptOrder(id));
    }

    @PatchMapping("/{id}/dispatch")
    public ResponseEntity<OrderStateResponseDto> dispatchOrder(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.dispatchOrder(id));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<OrderStateResponseDto> completeOrder(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(orderService.completeOrder(id));
    }
}
