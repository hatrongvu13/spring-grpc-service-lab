package com.htv.dispatch.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "delivery", uniqueConstraints = @UniqueConstraint(name = "uk_delivery_idempotency", columnNames = "idempotencyKey"))
public class DeliveryEntity {
    @Id
    public String id;
    @Column(nullable = false)
    public String idempotencyKey;
    public String status;
    public double originLat, originLon, destinationLat, destinationLon, weightKg;
    public long quotedAmountMinor;
    public String currency;
    public boolean priceFromFallback;
    public Instant createdAt;

    public DeliveryEntity() {
    }
}