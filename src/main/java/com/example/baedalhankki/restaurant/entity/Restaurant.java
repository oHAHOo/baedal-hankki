package com.example.baedalhankki.restaurant.entity;

import com.example.baedalhankki.global.exception.ErrorCode;

import com.example.baedalhankki.global.exception.ApiException;

import lombok.NoArgsConstructor;

import lombok.AccessLevel;

import lombok.Getter;

import com.example.baedalhankki.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class Restaurant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int deliveryFee;

    public static Restaurant create(String name, int deliveryFee) {
        if (deliveryFee < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Restaurant restaurant = new Restaurant();
        restaurant.name = name;
        restaurant.deliveryFee = deliveryFee;
        return restaurant;
    }

}
