package com.example.baedalhankki.restaurant.dto;

public record RestaurantResponseDto(
        Long restaurantId, String name, int deliveryFee, String paymentMethod) {
    public static RestaurantResponseDto of(Long restaurantId, String name, int deliveryFee, String paymentMethod) {
        return new RestaurantResponseDto(restaurantId, name, deliveryFee, paymentMethod);
    }
}
