package com.htv.commons.grpc.autoconfigure;

import com.htv.commons.grpc.interceptor.CorrelationIdInterceptor;
import com.htv.commons.grpc.interceptor.DeadlineGuardInterceptor;
import com.htv.commons.grpc.ping.ChainedPingService;
import com.htv.commons.grpc.ping.PingNextHop;

import io.grpc.ServerInterceptor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;

@AutoConfiguration
@ConditionalOnClass(ServerInterceptor.class)
@EnableConfigurationProperties(ServiceLabGrpcProperties.class)
public class ServiceLabGrpcServerAutoConfiguration {

    @Bean
    @Order(10)
    @GlobalServerInterceptor
    @ConditionalOnMissingBean(CorrelationIdInterceptor.class)
    public CorrelationIdInterceptor correlationIdInterceptor() {
        return new CorrelationIdInterceptor();
    }

    @Bean
    @Order(20)
    @GlobalServerInterceptor
    @ConditionalOnMissingBean(DeadlineGuardInterceptor.class)
    public DeadlineGuardInterceptor deadlineGuardInterceptor(
            ServiceLabGrpcProperties properties) {

        return new DeadlineGuardInterceptor(
                properties.getDefaultDeadlineMs(),
                properties.getMinimumRemainingMs()
        );
    }

    @Bean
    @ConditionalOnMissingBean(ChainedPingService.class)
    public ChainedPingService chainedPingService(
            ServiceLabGrpcProperties properties,
            PingNextHop nextHop) {

        return new ChainedPingService(
                properties.getServiceName(),
                nextHop
        );
    }

    @Bean
    @ConditionalOnMissingBean(PingNextHop.class)
    public PingNextHop noPingNextHop() {
        return PingNextHop.none();
    }
}