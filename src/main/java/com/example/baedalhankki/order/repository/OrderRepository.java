package com.example.baedalhankki.order.repository;

import com.example.baedalhankki.order.entity.Order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.customerId = :customerId")
    Optional<Order> findCustomerOrderForUpdate(
            @Param("orderId") Long orderId, @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.restaurantId = :restaurantId")
    Optional<Order> findRestaurantOrderForUpdate(
            @Param("orderId") Long orderId, @Param("restaurantId") Long restaurantId);

    @EntityGraph(attributePaths = {"items", "restaurant"})
    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);

    @EntityGraph(attributePaths = {"items", "restaurant"})
    List<Order> findByRestaurantIdOrderByIdDesc(Long restaurantId);
}
