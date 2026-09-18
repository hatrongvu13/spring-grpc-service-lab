package com.htv.telemetry.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "location_sample", indexes = @Index(name = "idx_vehicle_time", columnList = "vehicleId,recordedAt"))
public class LocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String vehicleId;
    public double latitude;
    public double longitude;
    public double speedKph;
    public Instant recordedAt;

    protected LocationEntity() {
    }

    public LocationEntity(String v, double la, double lo, double s, Instant t) {
        vehicleId = v;
        latitude = la;
        longitude = lo;
        speedKph = s;
        recordedAt = t;
    }
}