package com.example.baedalhankki.restaurant.repository;

import com.example.baedalhankki.restaurant.entity.Restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Optional<Restaurant> findByOwnerId(Long ownerId);
}
