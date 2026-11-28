package com.example.baedalhankki.refund.repository;

import com.example.baedalhankki.refund.entity.WebhookInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, Long> {
    boolean existsByPayloadHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WebhookInbox w where w.id = :id")
    Optional<WebhookInbox> findForUpdate(@Param("id") Long id);
    List<WebhookInbox> findByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Instant now, Pageable page);
}
