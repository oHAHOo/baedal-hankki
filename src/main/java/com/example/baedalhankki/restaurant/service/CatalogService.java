package com.example.baedalhankki.restaurant.service;

import lombok.RequiredArgsConstructor;

import com.example.baedalhankki.order.entity.PaymentMethod;
import com.example.baedalhankki.global.exception.ApiException;
import com.example.baedalhankki.global.exception.ErrorCode;
import com.example.baedalhankki.menu.repository.MenuRepository;
import com.example.baedalhankki.restaurant.dto.MenuResponseDto;
import com.example.baedalhankki.restaurant.dto.MenusResponseDto;
import com.example.baedalhankki.restaurant.dto.RestaurantResponseDto;
import com.example.baedalhankki.restaurant.entity.Restaurant;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatalogService {
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;

    public RestaurantResponseDto getRestaurant(Long id) {
        Restaurant restaurant =
                restaurants
                        .findById(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        return RestaurantResponseDto.of(
                restaurant.getId(), restaurant.getName(), restaurant.getDeliveryFee(), PaymentMethod.PAY_ON_DELIVERY.name());
    }

    public MenusResponseDto getMenus(Long id) {
        getRestaurant(id);
        return MenusResponseDto.of(
                menus.findByRestaurantIdAndIsActiveTrueOrderByIdAsc(id).stream()
                        .map(menu -> MenuResponseDto.of(menu.getId(), menu.getName(), menu.getPrice()))
                        .toList());
    }
}
