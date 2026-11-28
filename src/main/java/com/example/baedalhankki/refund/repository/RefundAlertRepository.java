package com.example.baedalhankki.refund.repository;

import com.example.baedalhankki.refund.entity.RefundAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface RefundAlertRepository extends JpaRepository<RefundAlert, Long> {
    boolean existsByOrderIdAndCode(Long orderId, String code);
    List<RefundAlert> findByAcknowledgedAtIsNullOrderByRaisedAtAsc(Pageable page);
    @Query("select a from RefundAlert a, Order o, Restaurant r where a.orderId = o.id "
            + "and o.restaurantId = r.id and r.ownerId = :ownerId order by a.raisedAt desc")
    List<RefundAlert> findOwnerAlerts(@Param("ownerId") Long ownerId, Pageable page);
}
