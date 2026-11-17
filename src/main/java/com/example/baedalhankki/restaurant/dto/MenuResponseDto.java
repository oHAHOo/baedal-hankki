package com.example.baedalhankki.restaurant.dto;

public record MenuResponseDto(Long menuId, String name, int price) {
    public static MenuResponseDto of(Long menuId, String name, int price) {
        return new MenuResponseDto(menuId, name, price);
    }
}
