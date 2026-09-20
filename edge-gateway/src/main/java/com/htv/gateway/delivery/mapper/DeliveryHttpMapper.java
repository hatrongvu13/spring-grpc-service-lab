package com.htv.gateway.delivery.mapper;

import com.google.protobuf.Timestamp;
import com.htv.gateway.delivery.DeliveryResponse;
import com.htv.proto.dispatch.v1.Delivery;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DeliveryHttpMapper {

    public DeliveryResponse toResponse(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getStatus().name(),
                delivery.getQuotedAmountMinor(),
                delivery.getCurrency(),
                delivery.getVehicleId(),
                toInstant(delivery),
                delivery.getPriceFromFallback()
        );
    }

    private Instant toInstant(Delivery delivery) {
        if (!delivery.hasCreatedAt()) {
            return null;
        }

        Timestamp timestamp = delivery.getCreatedAt();

        return Instant.ofEpochSecond(
                timestamp.getSeconds(),
                timestamp.getNanos()
        );
    }
}
