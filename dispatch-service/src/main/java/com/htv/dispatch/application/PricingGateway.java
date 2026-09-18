package com.htv.dispatch.application;

import com.htv.proto.pricing.v1.*;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cổng gọi pricing-service có cô lập lỗi.
 *
 * <p>Circuit breaker và bulkhead được cấu hình tường minh (không dùng
 * {@code ofDefaults()}) để phản ánh ngưỡng vận hành thực tế. Fallback
 * CHỈ áp dụng cho lỗi cho thấy pricing tạm không phục vụ được
 * (UNAVAILABLE / DEADLINE_EXCEEDED / circuit mở); mọi lỗi khác — kể cả
 * lỗi lập trình hay INVALID_ARGUMENT — được ném lên để không bị che.
 */
@Component
public class PricingGateway {

    private static final Logger log =
            LoggerFactory.getLogger(PricingGateway.class);

    private static final long FALLBACK_AMOUNT_MINOR = 25_000;
    private static final String FALLBACK_CURRENCY = "VND";

    private final PricingServiceGrpc.PricingServiceBlockingStub stub;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    public PricingGateway(PricingServiceGrpc.PricingServiceBlockingStub stub) {
        this.stub = stub;

        this.circuitBreaker = CircuitBreaker.of("pricing",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build());

        this.bulkhead = Bulkhead.of("pricing",
                BulkheadConfig.custom()
                        .maxConcurrentCalls(25)
                        .maxWaitDuration(Duration.ofMillis(50))
                        .build());
    }

    public QuotePriceResponse quote(QuotePriceRequest request) {
        Supplier<QuotePriceResponse> call = () -> stub.quotePrice(request);

        Supplier<QuotePriceResponse> guarded =
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        Bulkhead.decorateSupplier(bulkhead, call));

        try {
            return guarded.get();
        } catch (StatusRuntimeException grpcError) {
            if (isRetriableOutage(grpcError.getStatus())) {
                log.warn("Pricing tam khong phuc vu ({}), dung gia fallback",
                        grpcError.getStatus().getCode());
                return fallbackQuote();
            }
            // Loi that (INVALID_ARGUMENT, INTERNAL, ...) — khong che.
            throw grpcError;
        } catch (Exception resilienceError) {
            // Circuit mo / bulkhead day: pricing dang qua tai.
            log.warn("Pricing bi co lap boi resilience4j: {}, dung gia fallback",
                    resilienceError.toString());
            return fallbackQuote();
        }
    }

    private static boolean isRetriableOutage(Status status) {
        Status.Code code = status.getCode();
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED;
    }

    private static QuotePriceResponse fallbackQuote() {
        return QuotePriceResponse.newBuilder()
                .setAmountMinor(FALLBACK_AMOUNT_MINOR)
                .setCurrency(FALLBACK_CURRENCY)
                .setFromFallback(true)
                .build();
    }
}
