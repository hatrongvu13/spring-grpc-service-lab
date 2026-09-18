package com.htv.commons.grpc.autoconfigure;

import com.htv.commons.grpc.interceptor.CorrelationIdClientInterceptor;
import com.htv.commons.grpc.ping.GrpcPingNextHop;
import com.htv.commons.grpc.ping.PingNextHop;

import io.grpc.ManagedChannel;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.client.GrpcChannelFactory;

@AutoConfiguration
@ConditionalOnClass(GrpcChannelFactory.class)
@EnableConfigurationProperties(ServiceLabGrpcProperties.class)
public class ServiceLabGrpcClientAutoConfiguration {

    @Bean
    @Order(10)
    @GlobalClientInterceptor
    @ConditionalOnMissingBean(CorrelationIdClientInterceptor.class)
    public CorrelationIdClientInterceptor correlationIdClientInterceptor() {
        return new CorrelationIdClientInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(PingNextHop.class)
    public PingNextHop pingNextHop(
            ServiceLabGrpcProperties properties,
            ObjectProvider<GrpcChannelFactory> channelFactoryProvider) {

        String nextHop = properties.getPing().getNextHop();

        if (nextHop == null || nextHop.isBlank()) {
            return PingNextHop.none();
        }

        GrpcChannelFactory channelFactory =
                channelFactoryProvider.getIfAvailable();

        if (channelFactory == null) {
            return PingNextHop.none();
        }

        ManagedChannel channel =
                channelFactory.createChannel(nextHop);

        return new GrpcPingNextHop(
                channel,
                properties.getPing().getPerHopTimeoutMs()
        );
    }
}