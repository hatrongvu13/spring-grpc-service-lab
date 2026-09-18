package com.htv.commons.security.authorization;

import com.htv.commons.security.token.InternalTokenVerifier.InternalPrincipal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Chính sách phân quyền độc lập với gRPC interceptor.
 * <p>
 * Hỗ trợ:
 * - public service;
 * - PUBLIC method;
 * - scope theo full method name;
 * - deny hoặc allow các method chưa khai báo.
 */
public final class GrpcAuthorizationPolicy {

    public static final String PUBLIC = "PUBLIC";

    private final Map<String, Set<String>> rules;
    private final Set<String> publicServices;
    private final boolean denyUnmappedMethods;

    public GrpcAuthorizationPolicy(
            Map<String, List<String>> authorization,
            Collection<String> publicServices,
            boolean denyUnmappedMethods) {

        this.rules = normalizeRules(authorization);
        this.publicServices = normalizeValues(publicServices);
        this.denyUnmappedMethods = denyUnmappedMethods;
    }

    /**
     * Đánh giá quyền truy cập.
     *
     * @param fullMethodName dạng package.Service/Method
     * @param serviceName    dạng package.Service
     * @param principal      principal hoặc null nếu chưa xác thực
     */
    public AuthorizationDecision authorize(
            String fullMethodName,
            String serviceName,
            InternalPrincipal principal) {

        String normalizedMethod =
                normalize(fullMethodName);

        String normalizedService =
                normalize(serviceName);

        if (normalizedService != null
                && publicServices.contains(normalizedService)) {

            return AuthorizationDecision.granted(
                    "Public gRPC service"
            );
        }

        if (normalizedMethod == null) {
            return AuthorizationDecision.denied(
                    "Missing gRPC method name",
                    Set.of()
            );
        }

        Set<String> requiredScopes =
                rules.get(normalizedMethod);

        if (requiredScopes == null) {
            if (denyUnmappedMethods) {
                return AuthorizationDecision.denied(
                        "No authorization rule is configured",
                        Set.of()
                );
            }

            /*
             * Khi deny-unmapped-methods=false, method chưa map vẫn
             * cần token hợp lệ, nhưng không yêu cầu scope cụ thể.
             */
            if (principal == null) {
                return AuthorizationDecision.authenticationRequired(
                        "Authentication is required"
                );
            }

            return AuthorizationDecision.granted(
                    "Authenticated access to unmapped method"
            );
        }

        if (containsPublic(requiredScopes)) {
            return AuthorizationDecision.granted(
                    "Public gRPC method"
            );
        }

        if (principal == null) {
            return AuthorizationDecision.authenticationRequired(
                    "Authentication is required"
            );
        }

        if (requiredScopes.isEmpty()) {
            return AuthorizationDecision.granted(
                    "Authenticated access"
            );
        }

        /*
         * Chính sách hiện tại: phải có tất cả scope được khai báo.
         */
        Set<String> missingScopes =
                new LinkedHashSet<>(requiredScopes);

        missingScopes.removeAll(principal.scopes());

        if (!missingScopes.isEmpty()) {
            return AuthorizationDecision.denied(
                    "Required scope is missing",
                    Set.copyOf(missingScopes)
            );
        }

        return AuthorizationDecision.granted(
                "Required scopes are present"
        );
    }

    public boolean isPublic(
            String fullMethodName,
            String serviceName) {

        String normalizedService = normalize(serviceName);

        if (normalizedService != null
                && publicServices.contains(normalizedService)) {

            return true;
        }

        Set<String> requiredScopes =
                rules.get(normalize(fullMethodName));

        return requiredScopes != null
                && containsPublic(requiredScopes);
    }

    private static boolean containsPublic(
            Collection<String> scopes) {

        return scopes.stream()
                .anyMatch(PUBLIC::equalsIgnoreCase);
    }

    private static Map<String, Set<String>> normalizeRules(
            Map<String, List<String>> authorization) {

        if (authorization == null || authorization.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> normalized =
                new LinkedHashMap<>();

        authorization.forEach((method, scopes) -> {
            String normalizedMethod = normalize(method);

            if (normalizedMethod == null) {
                return;
            }

            normalized.put(
                    normalizedMethod,
                    normalizeValues(scopes)
            );
        });

        return Map.copyOf(normalized);
    }

    private static Set<String> normalizeValues(
            Collection<String> values) {

        if (values == null) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();

        for (String value : values) {
            String normalizedValue = normalize(value);

            if (normalizedValue != null) {
                normalized.add(normalizedValue);
            }
        }

        return Set.copyOf(normalized);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public record AuthorizationDecision(
            boolean granted,
            boolean authenticationRequired,
            String reason,
            Set<String> missingScopes
    ) {

        public AuthorizationDecision {
            reason = Objects.requireNonNullElse(
                    reason,
                    "No reason provided"
            );

            missingScopes = missingScopes == null
                    ? Set.of()
                    : Set.copyOf(missingScopes);
        }

        public static AuthorizationDecision granted(
                String reason) {

            return new AuthorizationDecision(
                    true,
                    false,
                    reason,
                    Set.of()
            );
        }

        public static AuthorizationDecision denied(
                String reason,
                Set<String> missingScopes) {

            return new AuthorizationDecision(
                    false,
                    false,
                    reason,
                    missingScopes
            );
        }

        public static AuthorizationDecision
        authenticationRequired(String reason) {

            return new AuthorizationDecision(
                    false,
                    true,
                    reason,
                    Set.of()
            );
        }
    }
}