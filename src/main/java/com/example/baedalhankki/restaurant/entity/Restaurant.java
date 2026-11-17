package com.example.baedalhankki.restaurant.entity;

import com.example.baedalhankki.global.exception.ErrorCode;

import com.example.baedalhankki.global.exception.ApiException;

import lombok.NoArgsConstructor;

import lombok.AccessLevel;

import lombok.Getter;

import com.example.baedalhankki.global.entity.BaseEntity;
import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;

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

    @Column(name = "owner_id", unique = true)
    private Long ownerId;

    public static Restaurant create(String name, int deliveryFee, Member owner) {
        if (deliveryFee < 0 || owner == null || owner.getId() == null
                || owner.getRole() != MemberRole.OWNER) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        Restaurant restaurant = new Restaurant();
        restaurant.ownerId = owner.getId();
        restaurant.name = name;
        restaurant.deliveryFee = deliveryFee;
        return restaurant;
    }

}
