package com.htv.commons.grpc.interceptor;

import io.grpc.*;
import org.slf4j.MDC;

import java.util.UUID;

public class CorrelationIdInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> HEADER =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> CTX_KEY = Context.key("service-lab.correlationId");

    private static final String MDC_KEY = "correlationId";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String incoming = headers.get(HEADER);
        final String correlationId =
                (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        Context ctx = Context.current().withValue(CTX_KEY, correlationId);
        ServerCall.Listener<ReqT> delegate = Contexts.interceptCall(ctx, call, headers, next);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            private void withMdc(Runnable action) {
                MDC.put(MDC_KEY, correlationId);
                try {
                    action.run();
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }

            @Override public void onMessage(ReqT message) { withMdc(() -> super.onMessage(message)); }
            @Override public void onHalfClose() { withMdc(super::onHalfClose); }
            @Override public void onCancel()    { withMdc(super::onCancel); }
            @Override public void onComplete()  { withMdc(super::onComplete); }
            @Override public void onReady()     { withMdc(super::onReady); }
        };
    }
}
