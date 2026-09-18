package com.htv.commons.grpc.ping;

import com.google.protobuf.Timestamp;
import com.htv.proto.ping.v1.PingRequest;
import com.htv.proto.ping.v1.PingResponse;
import com.htv.proto.ping.v1.PingServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@GrpcService
public class ChainedPingService extends PingServiceGrpc.PingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ChainedPingService.class);

    private final String serviceName;
    private final PingNextHop nextHop;

    public ChainedPingService(String serviceName, PingNextHop nextHop) {
        this.serviceName = serviceName;
        this.nextHop = nextHop;
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        long started = System.nanoTime();
        log.info("Nhan ping tu {} (con {} chang)", request.getFrom(), request.getHopsRemaining());

        List<String> trace = new ArrayList<>();

        if (nextHop.isPresent() && request.getHopsRemaining() > 0) {
            PingResponse downstream = nextHop.forward(
                    request.toBuilder()
                            .setFrom(serviceName)
                            .setHopsRemaining(request.getHopsRemaining() - 1)
                            .build());
            trace.addAll(downstream.getTraceList());
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        trace.add("%s <- %s (%dms)".formatted(serviceName, request.getFrom(), elapsedMs));

        Instant now = Instant.now();
        responseObserver.onNext(PingResponse.newBuilder()
                .setFrom(serviceName)
                .setNonce(request.getNonce())
                .addAllTrace(trace)
                .setAnsweredAt(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano()))
                .build());
        responseObserver.onCompleted();
    }
}
