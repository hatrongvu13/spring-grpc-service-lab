package com.htv.dispatch.config;

import com.htv.commons.security.grpc.InternalTokenClientInterceptor;
import com.htv.commons.security.token.InternalTokenIssuer;
import com.htv.proto.pricing.v1.PricingServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.List;

/**
 * Cấu hình stub gọi xuống pricing-service.
 *
 * Dispatch là client của pricing, nên channel phải đính internal token
 * do dispatch phát (audience = pricing-service). Nếu thiếu bước này,
 * pricing sẽ trả UNAUTHENTICATED và {@code PricingGateway} rơi vào
 * fallback — che mất lỗi thật.
 */
@Configuration(proxyBeanMethods = false)
public class DownstreamGrpcConfig {

    private static final String PRICING_CHANNEL = "pricing";
    private static final String PRICING_AUDIENCE = "pricing-service";

    @Bean
    PricingServiceGrpc.PricingServiceBlockingStub pricingStub(
            GrpcChannelFactory channelFactory,
            InternalTokenIssuer tokenIssuer) {

        Channel channel = channelFactory.createChannel(PRICING_CHANNEL);

        InternalTokenClientInterceptor tokenInterceptor =
                new InternalTokenClientInterceptor(
                        tokenIssuer,
                        PRICING_AUDIENCE,
                        List.of("pricing:quote"));

        return PricingServiceGrpc.newBlockingStub(
                ClientInterceptors.intercept(channel, tokenInterceptor));
    }
}
