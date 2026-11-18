package com.example.baedalhankki.restaurant.dto;

import java.util.List;

public record MenusResponseDto(List<MenuResponseDto> menus) {
    public static MenusResponseDto of(List<MenuResponseDto> menus) {
        return new MenusResponseDto(menus);
    }
}
