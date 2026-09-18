package com.htv.gateway.config;

import com.htv.commons.security.autoconfigure.ServiceLabSecurityProperties;
import com.htv.commons.security.grpc.InternalTokenClientInterceptor;
import com.htv.commons.security.token.InternalTokenIssuer;
import com.htv.proto.dispatch.v1.DispatchServiceGrpc;
import com.htv.proto.ping.v1.PingServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration(proxyBeanMethods = false)
public class DispatchGrpcClientConfiguration {

    public static final String DISPATCH_CHANNEL =
            "dispatch";

    public static final String DISPATCH_AUDIENCE =
            "dispatch-service";

    @Bean("authenticatedDispatchChannel")
    public Channel authenticatedDispatchChannel(
            GrpcChannelFactory channelFactory,
            InternalTokenIssuer tokenIssuer,
            ServiceLabSecurityProperties properties) {

        Channel channel = channelFactory.createChannel(
                DISPATCH_CHANNEL
        );

        InternalTokenClientInterceptor interceptor =
                new InternalTokenClientInterceptor(
                        tokenIssuer,
                        DISPATCH_AUDIENCE,
                        properties
                                .getTokenIssuer()
                                .getDefaultScopes()
                );

        return ClientInterceptors.intercept(
                channel,
                interceptor
        );
    }

    @Bean
    public PingServiceGrpc.PingServiceBlockingStub
    dispatchPingStub(
            @Qualifier("authenticatedDispatchChannel")
            Channel channel) {

        return PingServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public DispatchServiceGrpc.DispatchServiceBlockingStub
    dispatchServiceStub(
            @Qualifier("authenticatedDispatchChannel")
            Channel channel) {

        return DispatchServiceGrpc.newBlockingStub(channel);
    }
}
