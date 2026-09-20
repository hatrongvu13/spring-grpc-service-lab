package com.htv.gateway.controller;

import com.htv.gateway.delivery.CreateBody;
import com.htv.gateway.delivery.DeliveryResponse;
import com.htv.gateway.delivery.mapper.DeliveryHttpMapper;
import com.htv.proto.common.v1.GeoPoint;
import com.htv.proto.dispatch.v1.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {
    private final DispatchServiceGrpc.DispatchServiceBlockingStub dispatchService;
    private final DeliveryHttpMapper deliveryHttpMapper;

    public DeliveryController(DispatchServiceGrpc.DispatchServiceBlockingStub s, DeliveryHttpMapper deliveryHttpMapper) {
        dispatchService = s;
        this.deliveryHttpMapper = deliveryHttpMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryResponse> create(@RequestBody CreateBody body) {
        String idempotencyKey =
                normalizeIdempotencyKey(
                        body.idempotencyKey()
                );

        CreateDeliveryRequest request =
                CreateDeliveryRequest.newBuilder()
                        .setIdempotencyKey(idempotencyKey)
                        .setOrigin(
                                GeoPoint.newBuilder()
                                        .setLatitude(
                                                body.originLat()
                                        )
                                        .setLongitude(
                                                body.originLon()
                                        )
                                        .build()
                        )
                        .setDestination(
                                GeoPoint.newBuilder()
                                        .setLatitude(
                                                body.destinationLat()
                                        )
                                        .setLongitude(
                                                body.destinationLon()
                                        )
                                        .build()
                        )
                        .setWeightKg(body.weightKg())
                        .build();

        return Mono.fromCallable(
                        () -> dispatchService
                                .createDelivery(request)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(deliveryHttpMapper::toResponse);
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return value.trim();
    }
}