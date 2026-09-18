package com.htv.dispatch.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface DeliveryRepository extends JpaRepository<DeliveryEntity, String> {
    Optional<DeliveryEntity> findByIdempotencyKey(String key);
}