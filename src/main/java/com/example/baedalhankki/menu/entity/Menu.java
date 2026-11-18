package com.example.baedalhankki.menu.entity;

import com.example.baedalhankki.global.exception.ErrorCode;

import com.example.baedalhankki.global.exception.ApiException;

import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Getter;

import com.example.baedalhankki.global.entity.BaseEntity;
import com.example.baedalhankki.restaurant.entity.Restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Menu extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", insertable = false, updatable = false)
    private Restaurant restaurant;

    public static Menu create(Long restaurantId, String name, int price) {
        if (price < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Menu menu = new Menu();
        menu.restaurantId = restaurantId;
        menu.name = name;
        menu.price = price;
        menu.isActive = true;
        return menu;
    }

}
