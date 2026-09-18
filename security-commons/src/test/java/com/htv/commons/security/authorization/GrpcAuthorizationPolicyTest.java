package com.htv.commons.security.authorization;

import com.htv.commons.security.authorization.GrpcAuthorizationPolicy.AuthorizationDecision;
import com.htv.commons.security.token.InternalTokenVerifier.InternalPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khoá chặt việc authorization dùng ĐÚNG full gRPC method name
 * (package proto = servicelab.*, KHÔNG phải java_package com.htv.*)
 * và kiểm scope. Đây là bug thứ hai đã sửa trong application.yml.
 */
class GrpcAuthorizationPolicyTest {

    private static final String PING_METHOD =
            "servicelab.ping.v1.PingService/Ping";
    private static final String PING_SERVICE =
            "servicelab.ping.v1.PingService";

    private GrpcAuthorizationPolicy policy() {
        return new GrpcAuthorizationPolicy(
                Map.of(PING_METHOD, List.of("ping:execute")),
                List.of("grpc.health.v1.Health"),
                true);
    }

    private InternalPrincipal principalWithScopes(String... scopes) {
        return new InternalPrincipal(
                "edge-gateway", "edge-gateway", "edge-gateway",
                Set.of("dispatch-service"), Set.of(scopes), Set.of());
    }

    @Test
    void grantsWhenRealMethodNameAndScopeMatch() {
        AuthorizationDecision d = policy().authorize(
                PING_METHOD, PING_SERVICE,
                principalWithScopes("ping:execute"));
        assertThat(d.granted()).isTrue();
    }

    @Test
    void deniesWhenScopeMissing() {
        AuthorizationDecision d = policy().authorize(
                PING_METHOD, PING_SERVICE,
                principalWithScopes("delivery:create"));
        assertThat(d.granted()).isFalse();
        assertThat(d.missingScopes()).contains("ping:execute");
    }

    @Test
    void deniesUnmappedJavaPackageMethodName() {
        // Tên cũ (sai) trong config: java_package thay vì proto package.
        AuthorizationDecision d = policy().authorize(
                "com.htv.proto.ping.v1.PingService/Ping",
                "com.htv.proto.ping.v1.PingService",
                principalWithScopes("ping:execute"));
        assertThat(d.granted()).isFalse();
    }

    @Test
    void publicServiceBypassesAuthentication() {
        AuthorizationDecision d = policy().authorize(
                "grpc.health.v1.Health/Check",
                "grpc.health.v1.Health",
                null);
        assertThat(d.granted()).isTrue();
    }
}
