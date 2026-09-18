package com.htv.commons.security.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "service-lab.security")
public class ServiceLabSecurityProperties {

    /**
     * Bật xác thực và phân quyền cho gRPC server.
     * <p>
     * Mặc định tắt để các giai đoạn local hoặc lab có thể chạy
     * khi chưa cấu hình public key và internal token.
     */
    private boolean enabled = false;

    /**
     * Audience mà service hiện tại chấp nhận.
     * <p>
     * Ví dụ:
     * dispatch-service, pricing-service, telemetry-service.
     */
    private String audience;

    /**
     * Danh sách issuer được phép phát internal token.
     */
    private List<String> trustedIssuers =
            List.of("edge-gateway");

    /**
     * Public key dùng để xác minh internal JWT.
     * <p>
     * Ví dụ:
     * classpath:keys/internal-public.pem
     * file:./deploy/certs/gateway-token-pub.pem
     * file:/run/secrets/gateway-token-pub.pem
     */
    private String verificationKeyLocation;

    /**
     * Clock skew khi kiểm tra exp, nbf và iat.
     */
    private Duration clockSkew =
            Duration.ofSeconds(30);

    /**
     * Tên claim chứa scope.
     */
    private String scopeClaim = "scope";

    /**
     * Tên claim nhận diện service gọi request.
     */
    private String serviceClaim = "service_id";

    /**
     * Metadata header chứa bearer token.
     */
    private String authorizationHeader =
            "authorization";

    /**
     * Prefix đứng trước token.
     * <p>
     * Dấu cách phía sau Bearer là bắt buộc.
     */
    private String bearerPrefix = "Bearer ";

    /**
     * Các gRPC service được phép gọi không cần token.
     */
    private List<String> publicServices =
            List.of("grpc.health.v1.Health");

    /**
     * Full gRPC method name và danh sách scope bắt buộc.
     * <p>
     * Full method name sử dụng proto package, ví dụ:
     * <p>
     * servicelab.ping.v1.PingService/Ping
     */
    private Map<String, List<String>> authorization =
            new LinkedHashMap<>();

    /**
     * Từ chối method chưa được khai báo trong authorization.
     */
    private boolean denyUnmappedMethods = true;

    /**
     * Đẩy principal vào Spring SecurityContext.
     * <p>
     * Với streaming hoặc xử lý bất đồng bộ, nên ưu tiên
     * io.grpc.Context.
     */
    private boolean propagateSecurityContext = false;

    /**
     * Kích thước token tối đa.
     */
    @Positive
    private int maximumTokenLength = 8192;

    /**
     * Cấu hình phát internal token.
     * <p>
     * Thông thường chỉ edge-gateway bật phần này.
     */
    @Valid
    private final TokenIssuer tokenIssuer =
            new TokenIssuer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = normalize(audience);
    }

    public List<String> getTrustedIssuers() {
        return trustedIssuers;
    }

    public void setTrustedIssuers(
            List<String> trustedIssuers) {

        this.trustedIssuers =
                normalizeList(trustedIssuers);
    }

    public String getVerificationKeyLocation() {
        return verificationKeyLocation;
    }

    public void setVerificationKeyLocation(
            String verificationKeyLocation) {

        this.verificationKeyLocation =
                normalize(verificationKeyLocation);
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew == null
                ? Duration.ofSeconds(30)
                : clockSkew;
    }

    public String getScopeClaim() {
        return scopeClaim;
    }

    public void setScopeClaim(String scopeClaim) {
        this.scopeClaim = normalizeOrDefault(
                scopeClaim,
                "scope"
        );
    }

    public String getServiceClaim() {
        return serviceClaim;
    }

    public void setServiceClaim(String serviceClaim) {
        this.serviceClaim = normalizeOrDefault(
                serviceClaim,
                "service_id"
        );
    }

    public String getAuthorizationHeader() {
        return authorizationHeader;
    }

    public void setAuthorizationHeader(
            String authorizationHeader) {

        this.authorizationHeader =
                normalizeOrDefault(
                        authorizationHeader,
                        "authorization"
                );
    }

    public String getBearerPrefix() {
        return bearerPrefix;
    }

    public void setBearerPrefix(String bearerPrefix) {
        if (bearerPrefix == null
                || bearerPrefix.isBlank()) {

            this.bearerPrefix = "Bearer ";
            return;
        }

        /*
         * Không trim vì prefix mặc định cần giữ dấu cách:
         * "Bearer ".
         */
        this.bearerPrefix = bearerPrefix;
    }

    public List<String> getPublicServices() {
        return publicServices;
    }

    public void setPublicServices(
            List<String> publicServices) {

        this.publicServices =
                normalizeList(publicServices);
    }

    public Map<String, List<String>>
    getAuthorization() {

        return authorization;
    }

    public void setAuthorization(
            Map<String, List<String>> authorization) {

        Map<String, List<String>> normalized =
                new LinkedHashMap<>();

        if (authorization == null) {
            this.authorization = normalized;
            return;
        }

        authorization.forEach((method, scopes) -> {
            String normalizedMethod =
                    normalize(method);

            if (normalizedMethod == null) {
                return;
            }

            normalized.put(
                    normalizedMethod,
                    normalizeList(scopes)
            );
        });

        this.authorization = normalized;
    }

    public boolean isDenyUnmappedMethods() {
        return denyUnmappedMethods;
    }

    public void setDenyUnmappedMethods(
            boolean denyUnmappedMethods) {

        this.denyUnmappedMethods =
                denyUnmappedMethods;
    }

    public boolean isPropagateSecurityContext() {
        return propagateSecurityContext;
    }

    public void setPropagateSecurityContext(
            boolean propagateSecurityContext) {

        this.propagateSecurityContext =
                propagateSecurityContext;
    }

    public int getMaximumTokenLength() {
        return maximumTokenLength;
    }

    public void setMaximumTokenLength(
            int maximumTokenLength) {

        this.maximumTokenLength =
                maximumTokenLength;
    }

    public TokenIssuer getTokenIssuer() {
        return tokenIssuer;
    }

    public boolean isPublicMethod(
            String fullMethodName) {

        List<String> requiredScopes =
                authorization.get(fullMethodName);

        return requiredScopes != null
                && requiredScopes.stream()
                .anyMatch(
                        "PUBLIC"::equalsIgnoreCase
                );
    }

    public List<String> requiredScopes(
            String fullMethodName) {

        return authorization.getOrDefault(
                fullMethodName,
                List.of()
        );
    }

    /**
     * Cấu hình phát internal JWT.
     * <p>
     * Prefix đầy đủ:
     * service-lab.security.token-issuer
     */
    public static class TokenIssuer {

        /**
         * Bật khả năng phát internal token.
         * <p>
         * Chỉ nên bật tại edge-gateway hoặc một token broker
         * nội bộ được chỉ định rõ.
         */
        private boolean enabled = false;

        /**
         * Giá trị claim iss.
         */
        private String issuer = "edge-gateway";

        /**
         * Giá trị claim sub và service_id.
         */
        private String serviceId = "edge-gateway";

        /**
         * RSA private key dùng để ký JWT.
         * <p>
         * Chỉ service phát token được phép truy cập file này.
         */
        private String signingKeyLocation;

        /**
         * RSA public key tương ứng với private key.
         * <p>
         * NimbusJwtEncoder sử dụng cả cặp public/private key.
         */
        private String publicKeyLocation;

        /**
         * Thời gian tồn tại của token.
         * <p>
         * Nên ngắn, ví dụ 1 đến 5 phút.
         */
        private Duration tokenLifetime =
                Duration.ofMinutes(2);

        /**
         * Khoảng lùi nbf để chịu sai lệch đồng hồ nhỏ.
         */
        private Duration notBeforeSkew =
                Duration.ofSeconds(5);

        /**
         * Scopes mặc định được cấp bởi gateway.
         */
        private List<String> defaultScopes =
                List.of("ping:execute");

        /**
         * Thuật toán ký.
         * <p>
         * Với cấu hình RSA hiện tại chỉ dùng RS256.
         */
        private String algorithm = "RS256";

        /**
         * Key ID đưa vào JWT header.
         * <p>
         * Hữu ích khi triển khai xoay vòng khóa.
         */
        private String keyId =
                "gateway-internal-token-key";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = normalizeOrDefault(
                    issuer,
                    "edge-gateway"
            );
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = normalizeOrDefault(
                    serviceId,
                    "edge-gateway"
            );
        }

        public String getSigningKeyLocation() {
            return signingKeyLocation;
        }

        public void setSigningKeyLocation(
                String signingKeyLocation) {

            this.signingKeyLocation =
                    normalize(signingKeyLocation);
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(
                String publicKeyLocation) {

            this.publicKeyLocation =
                    normalize(publicKeyLocation);
        }

        public Duration getTokenLifetime() {
            return tokenLifetime;
        }

        public void setTokenLifetime(
                Duration tokenLifetime) {

            this.tokenLifetime =
                    tokenLifetime == null
                            ? Duration.ofMinutes(2)
                            : tokenLifetime;
        }

        public Duration getNotBeforeSkew() {
            return notBeforeSkew;
        }

        public void setNotBeforeSkew(
                Duration notBeforeSkew) {

            this.notBeforeSkew =
                    notBeforeSkew == null
                            ? Duration.ofSeconds(5)
                            : notBeforeSkew;
        }

        public List<String> getDefaultScopes() {
            return defaultScopes;
        }

        public void setDefaultScopes(
                List<String> defaultScopes) {

            this.defaultScopes =
                    normalizeList(defaultScopes);
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm =
                    normalizeOrDefault(
                            algorithm,
                            "RS256"
                    ).toUpperCase();
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = normalizeOrDefault(
                    keyId,
                    "gateway-internal-token-key"
            );
        }
    }

    private static List<String> normalizeList(
            List<String> values) {

        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .map(ServiceLabSecurityProperties::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeOrDefault(
            String value,
            String defaultValue) {

        String normalized = normalize(value);

        return normalized == null
                ? defaultValue
                : normalized;
    }
}