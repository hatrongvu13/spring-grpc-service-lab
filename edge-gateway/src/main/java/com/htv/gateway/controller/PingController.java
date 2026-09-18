package com.htv.gateway.controller;

import com.htv.proto.ping.v1.PingRequest;
import com.htv.proto.ping.v1.PingResponse;
import com.htv.proto.ping.v1.PingServiceGrpc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final PingServiceGrpc.PingServiceBlockingStub dispatchPingStub;

    public PingController(
            PingServiceGrpc.PingServiceBlockingStub dispatchPingStub) {

        this.dispatchPingStub = dispatchPingStub;
    }

    @GetMapping("/ping")
    public Mono<Map<String, Object>> ping(
            @RequestParam(defaultValue = "3") int hops) {

        int normalizedHops = Math.max(0, Math.min(hops, 10));

        return Mono.fromCallable(() -> invokePing(normalizedHops))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(5));
    }

    private Map<String, Object> invokePing(int hops) {
        String nonce = UUID.randomUUID().toString();

        PingRequest request = PingRequest.newBuilder()
                .setFrom("edge-gateway")
                .setNonce(nonce)
                .setHopsRemaining(hops)
                .build();

        PingResponse response = dispatchPingStub
                .withDeadlineAfter(4, TimeUnit.SECONDS)
                .ping(request);

        List<String> trace = response.getTraceList();

        return Map.of(
                "ok", nonce.equals(response.getNonce()),
                "answeredBy", response.getFrom(),
                "requestedHops", hops,
                "actualHops", trace.size(),
                "trace", trace
        );
    }
}