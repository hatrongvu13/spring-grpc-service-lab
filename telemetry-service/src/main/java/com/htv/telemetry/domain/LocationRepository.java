package com.htv.telemetry.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {
    Optional<LocationEntity> findTopByVehicleIdOrderByRecordedAtDesc(String vehicleId);
}