package com.htv.commons.security.autoconfigure;

import com.htv.commons.security.key.RsaKeyLoader;
import com.htv.commons.security.token.InternalTokenIssuer;
import com.htv.commons.security.token.JwtInternalTokenIssuer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@AutoConfiguration
@ConditionalOnClass({
        JwtEncoder.class,
        NimbusJwtEncoder.class
})
@EnableConfigurationProperties(
        ServiceLabSecurityProperties.class
)
@ConditionalOnProperty(
        prefix = "service-lab.security.token-issuer",
        name = "enabled",
        havingValue = "true"
)
public class ServiceLabTokenIssuerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RsaKeyLoader.class)
    public RsaKeyLoader rsaKeyLoader(
            ResourceLoader resourceLoader) {

        return new RsaKeyLoader(resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    public JwtEncoder internalJwtEncoder(
            RsaKeyLoader keyLoader,
            ServiceLabSecurityProperties properties) {

        ServiceLabSecurityProperties.TokenIssuer issuer =
                properties.getTokenIssuer();

        validateIssuerProperties(issuer);

        RSAPublicKey publicKey =
                keyLoader.loadPublicKey(
                        issuer.getPublicKeyLocation()
                );

        RSAPrivateKey privateKey =
                keyLoader.loadPrivateKey(
                        issuer.getSigningKeyLocation()
                );

        SignatureAlgorithm algorithm =
                resolveAlgorithm(issuer.getAlgorithm());

        return NimbusJwtEncoder
                .withKeyPair(publicKey, privateKey)
                .algorithm(algorithm)
                .jwkPostProcessor(
                        jwk -> jwk.keyID(
                                issuer.getKeyId()
                        )
                )
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(InternalTokenIssuer.class)
    public InternalTokenIssuer internalTokenIssuer(
            JwtEncoder jwtEncoder,
            ServiceLabSecurityProperties properties) {

        ServiceLabSecurityProperties.TokenIssuer issuer =
                properties.getTokenIssuer();

        return new JwtInternalTokenIssuer(
                jwtEncoder,
                issuer.getIssuer(),
                issuer.getServiceId(),
                issuer.getTokenLifetime(),
                issuer.getNotBeforeSkew(),
                resolveAlgorithm(issuer.getAlgorithm()),
                issuer.getKeyId()
        );
    }

    private static void validateIssuerProperties(
            ServiceLabSecurityProperties.TokenIssuer issuer) {

        requireText(
                issuer.getSigningKeyLocation(),
                "service-lab.security.token-issuer."
                        + "signing-key-location"
        );

        requireText(
                issuer.getPublicKeyLocation(),
                "service-lab.security.token-issuer."
                        + "public-key-location"
        );

        requireText(
                issuer.getIssuer(),
                "service-lab.security.token-issuer.issuer"
        );

        requireText(
                issuer.getServiceId(),
                "service-lab.security.token-issuer.service-id"
        );

        if (issuer.getTokenLifetime().isZero()
                || issuer.getTokenLifetime().isNegative()) {

            throw new IllegalStateException(
                    "service-lab.security.token-issuer."
                            + "token-lifetime must be positive"
            );
        }

        if (issuer.getNotBeforeSkew().isNegative()) {
            throw new IllegalStateException(
                    "service-lab.security.token-issuer."
                            + "not-before-skew must not be negative"
            );
        }
    }

    private static SignatureAlgorithm resolveAlgorithm(
            String algorithm) {

        return switch (algorithm) {
            case "RS256" -> SignatureAlgorithm.RS256;
            case "RS384" -> SignatureAlgorithm.RS384;
            case "RS512" -> SignatureAlgorithm.RS512;
            default -> throw new IllegalStateException(
                    "Unsupported internal token algorithm: "
                            + algorithm
                            + ". Supported values: "
                            + "RS256, RS384, RS512"
            );
        };
    }

    private static void requireText(
            String value,
            String propertyName) {

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    propertyName
                            + " is required when token issuer "
                            + "is enabled"
            );
        }
    }
}