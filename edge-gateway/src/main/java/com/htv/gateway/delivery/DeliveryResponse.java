package com.htv.gateway.delivery;

import java.time.Instant;

public record DeliveryResponse(
        String id,
        String status,
        long quotedAmountMinor,
        String currency,
        String vehicleId,
        Instant createdAt,
        boolean priceFromFallback
) {
}
