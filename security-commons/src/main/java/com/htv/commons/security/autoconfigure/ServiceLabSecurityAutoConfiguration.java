package com.htv.commons.security.autoconfigure;

import com.htv.commons.security.authorization.GrpcAuthorizationPolicy;
import com.htv.commons.security.grpc.AuthorizationInterceptor;
import com.htv.commons.security.token.InternalTokenVerifier;
import com.htv.commons.security.token.JwtInternalTokenVerifier;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfiguration
@ConditionalOnClass({
        JwtDecoder.class,
        GlobalServerInterceptor.class
})
@EnableConfigurationProperties(ServiceLabSecurityProperties.class)
@ConditionalOnProperty(
        prefix = "service-lab.security",
        name = "enabled",
        havingValue = "true"
)
public class ServiceLabSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GrpcAuthorizationPolicy.class)
    public GrpcAuthorizationPolicy grpcAuthorizationPolicy(
            ServiceLabSecurityProperties properties) {

        return new GrpcAuthorizationPolicy(
                properties.getAuthorization(),
                properties.getPublicServices(),
                properties.isDenyUnmappedMethods()
        );
    }

    @Bean
    @ConditionalOnMissingBean(InternalTokenVerifier.class)
    public InternalTokenVerifier internalTokenVerifier(
            JwtDecoder jwtDecoder,
            ServiceLabSecurityProperties properties) {

        return new JwtInternalTokenVerifier(
                jwtDecoder,
                properties.getAudience(),
                properties.getTrustedIssuers(),
                properties.getScopeClaim(),
                properties.getServiceClaim()
        );
    }

    @Bean
    @Order(30)
    @GlobalServerInterceptor
    @ConditionalOnMissingBean(AuthorizationInterceptor.class)
    public AuthorizationInterceptor authorizationInterceptor(
            InternalTokenVerifier tokenVerifier,
            GrpcAuthorizationPolicy authorizationPolicy,
            ServiceLabSecurityProperties properties) {

        return new AuthorizationInterceptor(
                tokenVerifier,
                authorizationPolicy,
                properties.getAuthorizationHeader(),
                properties.getBearerPrefix(),
                properties.getMaximumTokenLength(),
                properties.isPropagateSecurityContext()
        );
    }
}