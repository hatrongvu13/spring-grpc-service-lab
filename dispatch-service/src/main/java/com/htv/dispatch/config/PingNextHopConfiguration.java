package com.htv.dispatch.config;

import com.htv.commons.grpc.ping.GrpcPingNextHop;
import com.htv.commons.grpc.ping.PingNextHop;
import com.htv.commons.security.grpc.InternalTokenClientInterceptor;
import com.htv.commons.security.token.InternalTokenIssuer;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.List;

/**
 * Chặng ping kế tiếp: dispatch forward ping xuống pricing.
 *
 * <p>Ghi đè {@code PingNextHop} mặc định của grpc-commons (vốn tạo
 * channel trần không token). Channel ở đây đính internal token do
 * dispatch phát, audience = pricing-service, scope = ping:execute —
 * nếu không, pricing (đã bật security) sẽ trả UNAUTHENTICATED.
 */
@Configuration(proxyBeanMethods = false)
public class PingNextHopConfiguration {

    private static final String PRICING_CHANNEL = "pricing";
    private static final String PRICING_AUDIENCE = "pricing-service";
    private static final long PER_HOP_TIMEOUT_MS = 1_000;

    @Bean
    public PingNextHop pingNextHop(
            GrpcChannelFactory channelFactory,
            InternalTokenIssuer tokenIssuer) {

        Channel channel = channelFactory.createChannel(PRICING_CHANNEL);

        InternalTokenClientInterceptor tokenInterceptor =
                new InternalTokenClientInterceptor(
                        tokenIssuer,
                        PRICING_AUDIENCE,
                        List.of("ping:execute"));

        Channel authenticated =
                ClientInterceptors.intercept(channel, tokenInterceptor);

        return new GrpcPingNextHop(authenticated, PER_HOP_TIMEOUT_MS);
    }
}
