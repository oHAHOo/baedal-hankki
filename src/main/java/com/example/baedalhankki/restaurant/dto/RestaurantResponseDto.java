package com.example.baedalhankki.restaurant.dto;

import com.example.baedalhankki.order.entity.PaymentMethod;

import java.util.List;

public record RestaurantResponseDto(
        Long restaurantId, String name, int deliveryFee, String paymentMethod, List<PaymentMethod> paymentMethods) {
    public static RestaurantResponseDto of(Long restaurantId, String name, int deliveryFee, String paymentMethod) {
        return new RestaurantResponseDto(restaurantId, name, deliveryFee, paymentMethod,
                List.of(PaymentMethod.PAY_ON_DELIVERY, PaymentMethod.ONLINE));
    }
}
