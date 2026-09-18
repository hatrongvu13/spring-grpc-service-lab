package com.htv.commons.grpc.interceptor;

import io.grpc.*;

import java.util.UUID;

public class CorrelationIdClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String id = CorrelationIdInterceptor.CTX_KEY.get();
                headers.put(CorrelationIdInterceptor.HEADER,
                        id != null ? id : UUID.randomUUID().toString());
                super.start(responseListener, headers);
            }
        };
    }
}
