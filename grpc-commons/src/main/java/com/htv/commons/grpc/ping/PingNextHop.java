package com.htv.commons.grpc.ping;

import com.htv.proto.ping.v1.PingRequest;
import com.htv.proto.ping.v1.PingResponse;

public interface PingNextHop {

    boolean isPresent();

    PingResponse forward(PingRequest request);

    static PingNextHop none() {
        return new PingNextHop() {
            @Override public boolean isPresent() { return false; }
            @Override public PingResponse forward(PingRequest r) {
                throw new IllegalStateException("Khong co chang ke tiep");
            }
        };
    }
}
