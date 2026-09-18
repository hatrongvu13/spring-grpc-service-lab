package com.htv.commons.grpc.ping;

import com.htv.proto.ping.v1.PingRequest;
import com.htv.proto.ping.v1.PingResponse;
import com.htv.proto.ping.v1.PingServiceGrpc;

import io.grpc.Channel;
import java.util.concurrent.TimeUnit;

public class GrpcPingNextHop implements PingNextHop {

    private final Channel channel;
    private final long perHopTimeoutMs;

    public GrpcPingNextHop(Channel channel, long perHopTimeoutMs) {
        this.channel = channel;
        this.perHopTimeoutMs = perHopTimeoutMs;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public PingResponse forward(PingRequest request) {
        return PingServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(perHopTimeoutMs, TimeUnit.MILLISECONDS)
                .ping(request);
    }
}
