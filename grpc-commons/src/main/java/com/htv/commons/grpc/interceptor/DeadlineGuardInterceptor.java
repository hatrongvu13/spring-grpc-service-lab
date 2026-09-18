package com.htv.commons.grpc.interceptor;

import io.grpc.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeadlineGuardInterceptor implements ServerInterceptor {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "servicelab-deadline");
                t.setDaemon(true);
                return t;
            });

    private final long defaultTimeoutMs;
    private final long minimumRemainingMs;

    public DeadlineGuardInterceptor(long defaultTimeoutMs, long minimumRemainingMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.minimumRemainingMs = minimumRemainingMs;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        Deadline deadline = Context.current().getDeadline();

        if (deadline != null && deadline.timeRemaining(TimeUnit.MILLISECONDS) < minimumRemainingMs) {
            call.close(Status.DEADLINE_EXCEEDED
                    .withDescription("Thoi gian con lai qua ngan de xu ly"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        Context ctx = (deadline == null)
                ? Context.current().withDeadlineAfter(defaultTimeoutMs, TimeUnit.MILLISECONDS, SCHEDULER)
                : Context.current();

        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
