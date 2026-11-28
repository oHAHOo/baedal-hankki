package com.example.baedalhankki.refund.repository;

import com.example.baedalhankki.refund.entity.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import com.example.baedalhankki.refund.entity.RefundStatus;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findFirstByOrderIdOrderByIdDesc(Long orderId);
    List<RefundRequest> findByOrderIdOrderByIdAsc(Long orderId);
    List<RefundRequest> findByStatusInOrderByRequestedAtAsc(List<RefundStatus> statuses, Pageable page);
    @Query("select r.orderId from RefundRequest r where r.id = :id")
    Optional<Long> findOrderId(@Param("id") Long id);
}
