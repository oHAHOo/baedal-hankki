package com.example.baedalhankki.payment.repository;

import com.example.baedalhankki.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    @Query("select p.orderId from Payment p where (p.nextCheckAt is null or p.nextCheckAt <= :now) "
            + "and (p.leaseUntil is null or p.leaseUntil <= :now) order by p.nextCheckAt, p.id")
    List<Long> findDueOrderIds(@Param("now") Instant now, Pageable page);
}
