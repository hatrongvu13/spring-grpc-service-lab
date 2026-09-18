package com.htv.dispatch.grpc;

import com.google.protobuf.Timestamp;
import com.htv.dispatch.application.PricingGateway;
import com.htv.dispatch.domain.*;
import com.htv.proto.dispatch.v1.*;
import com.htv.proto.pricing.v1.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DispatchGrpcService extends DispatchServiceGrpc.DispatchServiceImplBase {

    private static final Logger log =
            LoggerFactory.getLogger(DispatchGrpcService.class);

    private final DeliveryRepository repo;
    private final PricingGateway pricing;

    public DispatchGrpcService(DeliveryRepository r, PricingGateway p) {
        repo = r;
        pricing = p;
    }

    @Override
    @Transactional
    public void createDelivery(CreateDeliveryRequest req, StreamObserver<Delivery> out) {
        if (req.getIdempotencyKey().isBlank() || !req.hasOrigin() || !req.hasDestination() || req.getWeightKg() <= 0) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("invalid delivery request").asRuntimeException());
            return;
        }
        var existing = repo.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            out.onNext(toProto(existing.get()));
            out.onCompleted();
            return;
        }
        var q = pricing.quote(QuotePriceRequest.newBuilder().setOrigin(req.getOrigin()).setDestination(req.getDestination()).setWeightKg(req.getWeightKg()).build());
        var e = new DeliveryEntity();
        e.id = UUID.randomUUID().toString();
        e.idempotencyKey = req.getIdempotencyKey();
        e.status = DeliveryStatus.DELIVERY_STATUS_PENDING.name();
        e.originLat = req.getOrigin().getLatitude();
        e.originLon = req.getOrigin().getLongitude();
        e.destinationLat = req.getDestination().getLatitude();
        e.destinationLon = req.getDestination().getLongitude();
        e.weightKg = req.getWeightKg();
        e.quotedAmountMinor = q.getAmountMinor();
        e.currency = q.getCurrency();
        e.priceFromFallback = q.getFromFallback();
        e.createdAt = Instant.now();
        repo.save(e);
        out.onNext(toProto(e));
        out.onCompleted();
    }

    private Delivery toProto(DeliveryEntity e) {
        return Delivery.newBuilder().setId(e.id).setStatus(DeliveryStatus.valueOf(e.status)).setQuotedAmountMinor(e.quotedAmountMinor).setCurrency(e.currency).setPriceFromFallback(e.priceFromFallback).setCreatedAt(Timestamp.newBuilder().setSeconds(e.createdAt.getEpochSecond()).setNanos(e.createdAt.getNano())).build();
    }

    @Override
    public void watchDelivery(WatchDeliveryRequest req, StreamObserver<DeliveryEvent> out) {
        repo.findById(req.getDeliveryId()).ifPresentOrElse(e -> {
            out.onNext(DeliveryEvent.newBuilder().setDeliveryId(e.id).setStatus(DeliveryStatus.valueOf(e.status)).setOccurredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond())).build());
            out.onCompleted();
        }, () -> out.onError(Status.NOT_FOUND.withDescription("delivery not found: " + req.getDeliveryId()).asRuntimeException()));
    }

    /**
     * Bidirectional channel với tài xế:
     * - Nhận heartbeat / job-ack từ tài xế.
     * - Với mỗi job-ack accepted, phát AssignJob xác nhận.
     * - Với job-ack bị từ chối, phát CancelJob.
     * Lỗi từ client được LOG (không nuốt) và stream được đóng đúng cách.
     */
    @Override
    public StreamObserver<DriverMessage> dispatchChannel(StreamObserver<DispatchCommand> out) {
        return new StreamObserver<>() {
            @Override
            public void onNext(DriverMessage message) {
                switch (message.getPayloadCase()) {
                    case HEARTBEAT -> {
                        DriverHeartbeat hb = message.getHeartbeat();
                        log.debug("Heartbeat tu xe {} tai ({}, {})",
                                hb.getVehicleId(),
                                hb.getPoint().getLatitude(),
                                hb.getPoint().getLongitude());
                    }
                    case JOB_ACK -> {
                        JobAck ack = message.getJobAck();
                        if (ack.getAccepted()) {
                            out.onNext(DispatchCommand.newBuilder()
                                    .setAssignJob(AssignJob.newBuilder()
                                            .setDeliveryId(ack.getDeliveryId()))
                                    .build());
                        } else {
                            out.onNext(DispatchCommand.newBuilder()
                                    .setCancelJob(CancelJob.newBuilder()
                                            .setDeliveryId(ack.getDeliveryId())
                                            .setReason("driver rejected job"))
                                    .build());
                        }
                    }
                    case PAYLOAD_NOT_SET -> log.warn("DriverMessage rong, bo qua");
                }
            }

            @Override
            public void onError(Throwable t) {
                // KHONG nuot loi: ghi log de giu tin hieu quan trac.
                log.warn("dispatchChannel loi tu client: {}", t.toString());
            }

            @Override
            public void onCompleted() {
                out.onCompleted();
            }
        };
    }
}
