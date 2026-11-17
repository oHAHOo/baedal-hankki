package com.example.baedalhankki.order.repository;

import com.example.baedalhankki.order.entity.Order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    @EntityGraph(attributePaths = {"items", "restaurant"})
    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);

    @EntityGraph(attributePaths = {"items", "restaurant"})
    List<Order> findByRestaurantIdOrderByIdDesc(Long restaurantId);
}
