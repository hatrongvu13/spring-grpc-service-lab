package com.htv.commons.security.token;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JwtInternalTokenIssuer
        implements InternalTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String serviceId;
    private final Duration tokenLifetime;
    private final Duration notBeforeSkew;
    private final SignatureAlgorithm algorithm;
    private final String keyId;
    private final Clock clock;

    public JwtInternalTokenIssuer(
            JwtEncoder jwtEncoder,
            String issuer,
            String serviceId,
            Duration tokenLifetime,
            Duration notBeforeSkew,
            SignatureAlgorithm algorithm,
            String keyId) {

        this(
                jwtEncoder,
                issuer,
                serviceId,
                tokenLifetime,
                notBeforeSkew,
                algorithm,
                keyId,
                Clock.systemUTC()
        );
    }

    JwtInternalTokenIssuer(
            JwtEncoder jwtEncoder,
            String issuer,
            String serviceId,
            Duration tokenLifetime,
            Duration notBeforeSkew,
            SignatureAlgorithm algorithm,
            String keyId,
            Clock clock) {

        this.jwtEncoder = Objects.requireNonNull(
                jwtEncoder,
                "jwtEncoder must not be null"
        );

        this.issuer = requireText(
                issuer,
                "issuer"
        );

        this.serviceId = requireText(
                serviceId,
                "serviceId"
        );

        this.tokenLifetime = requirePositiveDuration(
                tokenLifetime,
                "tokenLifetime"
        );

        this.notBeforeSkew = requireNonNegativeDuration(
                notBeforeSkew,
                "notBeforeSkew"
        );

        this.algorithm = Objects.requireNonNull(
                algorithm,
                "algorithm must not be null"
        );

        this.keyId = requireText(
                keyId,
                "keyId"
        );

        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Override
    public String issue(
            String audience,
            Collection<String> scopes) {

        String normalizedAudience =
                requireText(audience, "audience");

        String normalizedScopes =
                normalizeScopes(scopes);

        Instant issuedAt = clock.instant();
        Instant expiresAt =
                issuedAt.plus(tokenLifetime);

        JwtClaimsSet.Builder claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .issuer(issuer)
                        .subject(serviceId)
                        .audience(
                                List.of(normalizedAudience)
                        )
                        .issuedAt(issuedAt)
                        .notBefore(
                                issuedAt.minus(notBeforeSkew)
                        )
                        .expiresAt(expiresAt)
                        .claim("service_id", serviceId);

        if (!normalizedScopes.isBlank()) {
            claims.claim(
                    "scope",
                    normalizedScopes
            );
        }

        JwsHeader header = JwsHeader
                .with(algorithm)
                .type("JWT")
                .keyId(keyId)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(
                        header,
                        claims.build()
                )
        ).getTokenValue();
    }

    private static String normalizeScopes(
            Collection<String> scopes) {

        if (scopes == null || scopes.isEmpty()) {
            return "";
        }

        return scopes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static Duration requirePositiveDuration(
            Duration value,
            String fieldName) {

        Objects.requireNonNull(
                value,
                fieldName + " must not be null"
        );

        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }

        return value;
    }

    private static Duration requireNonNegativeDuration(
            Duration value,
            String fieldName) {

        Objects.requireNonNull(
                value,
                fieldName + " must not be null"
        );

        if (value.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be negative"
            );
        }

        return value;
    }

    private static String requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }
}