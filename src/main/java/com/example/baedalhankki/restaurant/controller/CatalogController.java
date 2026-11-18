package com.example.baedalhankki.restaurant.controller;

import lombok.RequiredArgsConstructor;

import com.example.baedalhankki.restaurant.dto.MenusResponseDto;
import com.example.baedalhankki.restaurant.dto.RestaurantResponseDto;
import com.example.baedalhankki.restaurant.service.CatalogService;

import org.springframework.http.ResponseEntity;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponseDto> getRestaurant(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(catalogService.getRestaurant(id));
    }

    @GetMapping("/{id}/menus")
    public ResponseEntity<MenusResponseDto> getMenus(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(catalogService.getMenus(id));
    }
}
