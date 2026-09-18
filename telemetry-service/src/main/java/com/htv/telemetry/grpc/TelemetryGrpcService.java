package com.htv.telemetry.grpc;

import com.htv.proto.telemetry.v1.*;
import com.htv.telemetry.domain.*;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.*;

@Service
public class TelemetryGrpcService extends TelemetryServiceGrpc.TelemetryServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(TelemetryGrpcService.class);
    private final LocationRepository repo;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public TelemetryGrpcService(LocationRepository r) {
        repo = r;
    }

    @Override
    public StreamObserver<LocationSample> pushLocation(StreamObserver<PushLocationSummary> out) {
        return new StreamObserver<>() {
            long ok, bad;

            public void onNext(LocationSample s) {
                if (s.getVehicleId().isBlank() || !s.hasPoint()) {
                    bad++;
                    return;
                }
                Instant t = Instant.ofEpochSecond(s.getRecordedAt().getSeconds(), s.getRecordedAt().getNanos());
                repo.save(new LocationEntity(s.getVehicleId(), s.getPoint().getLatitude(), s.getPoint().getLongitude(), s.getSpeedKph(), t));
                ok++;
            }

            public void onError(Throwable t) {
                log.warn("pushLocation loi tu client (da nhan {} ok, {} bad): {}", ok, bad, t.toString());
            }

            public void onCompleted() {
                out.onNext(PushLocationSummary.newBuilder().setAccepted(ok).setRejected(bad).build());
                out.onCompleted();
            }
        };
    }

    @Override
    public void watchVehicle(WatchVehicleRequest req, StreamObserver<LocationSample> out) {
        var task = scheduler.scheduleAtFixedRate(() -> repo.findTopByVehicleIdOrderByRecordedAtDesc(req.getVehicleId()).ifPresent(e -> out.onNext(LocationSample.newBuilder().setVehicleId(e.vehicleId).setPoint(com.htv.proto.common.v1.GeoPoint.newBuilder().setLatitude(e.latitude).setLongitude(e.longitude)).setSpeedKph(e.speedKph).setRecordedAt(Timestamp.newBuilder().setSeconds(e.recordedAt.getEpochSecond()).setNanos(e.recordedAt.getNano())).build())), 0, 2, TimeUnit.SECONDS);
        io.grpc.Context.current().addListener(c -> {
            task.cancel(true);
            out.onCompleted();
        }, Runnable::run);
    }
}
