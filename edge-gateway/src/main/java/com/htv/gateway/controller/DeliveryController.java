package com.htv.gateway.controller;

import com.htv.proto.common.v1.GeoPoint;
import com.htv.proto.dispatch.v1.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {
    private final DispatchServiceGrpc.DispatchServiceBlockingStub stub;

    public DeliveryController(DispatchServiceGrpc.DispatchServiceBlockingStub s) {
        stub = s;
    }

    public record CreateBody(double originLat, double originLon, double destinationLat, double destinationLon,
                             double weightKg, String idempotencyKey) {
    }

    @PostMapping
    public Mono<Delivery> create(@RequestBody CreateBody b) {
        return Mono.fromCallable(() -> stub.createDelivery(CreateDeliveryRequest.newBuilder().setIdempotencyKey(b.idempotencyKey() == null ? UUID.randomUUID().toString() : b.idempotencyKey()).setOrigin(GeoPoint.newBuilder().setLatitude(b.originLat()).setLongitude(b.originLon())).setDestination(GeoPoint.newBuilder().setLatitude(b.destinationLat()).setLongitude(b.destinationLon())).setWeightKg(b.weightKg()).build())).subscribeOn(Schedulers.boundedElastic());
    }
}