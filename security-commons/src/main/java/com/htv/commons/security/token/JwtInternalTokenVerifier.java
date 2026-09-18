package com.htv.commons.security.token;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Xác minh internal JWT bằng JwtDecoder do Spring Security cung cấp.
 *
 * JwtDecoder chịu trách nhiệm:
 * - giải mã JWT;
 * - xác minh chữ ký;
 * - kiểm tra exp và nbf theo validator được cấu hình.
 *
 * Class này kiểm tra thêm:
 * - audience;
 * - trusted issuer;
 * - service identity;
 * - scope.
 */
public final class JwtInternalTokenVerifier
        implements InternalTokenVerifier {

    private final JwtDecoder jwtDecoder;
    private final String expectedAudience;
    private final Set<String> trustedIssuers;
    private final String scopeClaim;
    private final String serviceClaim;

    public JwtInternalTokenVerifier(
            JwtDecoder jwtDecoder,
            String expectedAudience,
            Collection<String> trustedIssuers,
            String scopeClaim,
            String serviceClaim) {

        this.jwtDecoder = Objects.requireNonNull(
                jwtDecoder,
                "jwtDecoder must not be null"
        );

        this.expectedAudience = requireText(
                expectedAudience,
                "expectedAudience"
        );

        this.trustedIssuers = normalizeValues(trustedIssuers);

        if (this.trustedIssuers.isEmpty()) {
            throw new IllegalArgumentException(
                    "trustedIssuers must not be empty"
            );
        }

        this.scopeClaim = defaultIfBlank(
                scopeClaim,
                "scope"
        );

        this.serviceClaim = defaultIfBlank(
                serviceClaim,
                "service_id"
        );
    }

    @Override
    public InternalPrincipal verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InternalTokenVerificationException(
                    "Internal token is empty"
            );
        }

        final Jwt jwt;

        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw new InternalTokenVerificationException(
                    "Internal token cannot be verified",
                    exception
            );
        }

        validateAudience(jwt);
        validateIssuer(jwt);

        String subject = normalize(jwt.getSubject());
        /*
         * Đọc issuer bằng getClaimAsString thay vì getIssuer():
         * getIssuer() ép claim 'iss' thành java.net.URL, nên một
         * issuer dạng service-name ("edge-gateway") sẽ ném
         * IllegalArgumentException. Ở đây issuer là định danh service,
         * không phải URL.
         */
        String issuer = normalize(jwt.getClaimAsString("iss"));

        String serviceId = normalize(
                jwt.getClaimAsString(serviceClaim)
        );

        /*
         * Cho phép subject làm fallback service identity.
         * Với token mới, nên luôn phát service_id rõ ràng.
         */
        if (serviceId == null) {
            serviceId = subject;
        }

        if (serviceId == null) {
            throw new InternalTokenVerificationException(
                    "Internal token is missing both "
                            + serviceClaim
                            + " and sub claims"
            );
        }

        Set<String> scopes = extractScopes(jwt);
        Set<String> audiences =
                new LinkedHashSet<>(jwt.getAudience());

        Collection<GrantedAuthority> authorities =
                scopes.stream()
                        .map(scope -> new SimpleGrantedAuthority(
                                "SCOPE_" + scope
                        ))
                        .collect(Collectors.toUnmodifiableSet());

        return new InternalPrincipal(
                subject,
                issuer,
                serviceId,
                audiences,
                scopes,
                authorities
        );
    }

    private void validateAudience(Jwt jwt) {
        List<String> audiences = jwt.getAudience();

        if (audiences == null
                || !audiences.contains(expectedAudience)) {

            throw new InternalTokenVerificationException(
                    "Internal token does not contain required audience: "
                            + expectedAudience
            );
        }
    }

    private void validateIssuer(Jwt jwt) {
        /*
         * Dùng getClaimAsString('iss') thay cho getIssuer() vì
         * getIssuer() ép claim thành URL; issuer nội bộ là
         * service-name nên không phải URL.
         */
        String issuer = normalize(jwt.getClaimAsString("iss"));

        if (issuer == null || !trustedIssuers.contains(issuer)) {
            throw new InternalTokenVerificationException(
                    "Internal token was issued by an untrusted issuer"
            );
        }
    }

    private Set<String> extractScopes(Jwt jwt) {
        Object rawClaim = jwt.getClaim(scopeClaim);

        if (rawClaim == null) {
            /*
             * Một số issuer sử dụng "scp" thay cho "scope".
             */
            rawClaim = jwt.getClaim("scp");
        }

        if (rawClaim == null) {
            return Set.of();
        }

        if (rawClaim instanceof String scopeText) {
            return Arrays.stream(scopeText.trim().split("\\s+"))
                    .map(JwtInternalTokenVerifier::normalize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        }

        if (rawClaim instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(JwtInternalTokenVerifier::normalize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        }

        throw new InternalTokenVerificationException(
                "Claim " + scopeClaim
                        + " must be a string or a collection"
        );
    }

    private static Set<String> normalizeValues(
            Collection<String> values) {

        if (values == null) {
            return Set.of();
        }

        return values.stream()
                .map(JwtInternalTokenVerifier::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireText(
            String value,
            String fieldName) {

        String normalized = normalize(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    private static String defaultIfBlank(
            String value,
            String defaultValue) {

        String normalized = normalize(value);
        return normalized == null
                ? defaultValue
                : normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}