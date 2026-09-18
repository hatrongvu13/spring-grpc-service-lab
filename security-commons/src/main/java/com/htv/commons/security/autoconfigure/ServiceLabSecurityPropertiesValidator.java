package com.htv.commons.security.autoconfigure;

import org.springframework.util.StringUtils;

public final class ServiceLabSecurityPropertiesValidator {

    private ServiceLabSecurityPropertiesValidator() {
    }

    public static void validate(
            ServiceLabSecurityProperties properties) {

        requireText(
                properties.getAudience(),
                "service-lab.security.audience"
        );

        requireText(
                properties.getVerificationKeyLocation(),
                "service-lab.security.verification-key-location"
        );

        if (properties.getTrustedIssuers().isEmpty()) {
            throw new IllegalStateException(
                    "service-lab.security.trusted-issuers "
                            + "must contain at least one issuer"
            );
        }

        if (properties.getClockSkew().isNegative()) {
            throw new IllegalStateException(
                    "service-lab.security.clock-skew "
                            + "must not be negative"
            );
        }

        if (properties.getMaximumTokenLength() <= 0) {
            throw new IllegalStateException(
                    "service-lab.security.maximum-token-length "
                            + "must be greater than zero"
            );
        }
    }

    private static void requireText(
            String value,
            String propertyName) {

        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    propertyName
                            + " is required when "
                            + "service-lab.security.enabled=true"
            );
        }
    }
}