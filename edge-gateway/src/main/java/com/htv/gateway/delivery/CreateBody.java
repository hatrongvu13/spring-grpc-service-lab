package com.htv.gateway.delivery;

public record CreateBody(double originLat, double originLon, double destinationLat, double destinationLon,
                         double weightKg, String idempotencyKey) {
}
