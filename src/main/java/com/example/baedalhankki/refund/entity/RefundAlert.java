package com.example.baedalhankki.refund.entity;

import com.example.baedalhankki.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAlert extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(nullable = false, length = 80)
    private String code;
    private Instant raisedAt;
    private Instant acknowledgedAt;
    private Long acknowledgedBy;
    @Column(length = 500)
    private String note;

    public static RefundAlert create(Long orderId, String code, Instant now) {
        RefundAlert alert = new RefundAlert();
        alert.orderId = orderId;
        alert.code = code;
        alert.raisedAt = now;
        return alert;
    }

    public void acknowledge(Long actorId, String note, Instant now) {
        acknowledgedBy = actorId;
        acknowledgedAt = now;
        this.note = note;
    }
}
