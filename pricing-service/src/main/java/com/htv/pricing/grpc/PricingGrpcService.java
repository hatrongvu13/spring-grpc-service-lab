package com.htv.pricing.grpc;

import com.htv.proto.pricing.v1.*;
import com.htv.pricing.domain.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class PricingGrpcService extends PricingServiceGrpc.PricingServiceImplBase {
    private final QuoteAuditRepository repo;

    public PricingGrpcService(QuoteAuditRepository r) {
        repo = r;
    }

    @Override
    public void quotePrice(QuotePriceRequest req, StreamObserver<QuotePriceResponse> out) {
        if (req.getWeightKg() <= 0) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("weight_kg must be positive").asRuntimeException());
            return;
        }
        if (req.getWeightKg() > 500) {
            out.onError(Status.UNAVAILABLE.withDescription("simulated pricing outage").asRuntimeException());
            return;
        }
        long amount = Math.round(15000 + req.getWeightKg() * 4200);
        repo.save(new QuoteAudit(req.getWeightKg(), amount));
        out.onNext(QuotePriceResponse.newBuilder().setAmountMinor(amount).setCurrency("VND").setFromFallback(false).build());
        out.onCompleted();
    }
}