package com.htv.commons.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@AutoConfiguration(
        before = ServiceLabSecurityAutoConfiguration.class
)
@ConditionalOnProperty(
        prefix = "service-lab.security",
        name = "enabled",
        havingValue = "true"
)
public class ServiceLabJwtDecoderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder internalJwtDecoder(
            ServiceLabSecurityProperties properties,
            ResourceLoader resourceLoader) {

        RSAPublicKey publicKey = loadPublicKey(
                resourceLoader,
                properties.getVerificationKeyLocation()
        );

        return NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();
    }

    private RSAPublicKey loadPublicKey(
            ResourceLoader resourceLoader,
            String location) {

        try {
            Resource resource =
                    resourceLoader.getResource(location);

            if (!resource.exists()) {
                throw new IllegalStateException(
                        "JWT verification key does not exist: "
                                + location
                );
            }

            String pem;

            try (InputStream inputStream =
                         resource.getInputStream()) {

                pem = new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            String encodedKey = pem
                    .replace(
                            "-----BEGIN PUBLIC KEY-----",
                            ""
                    )
                    .replace(
                            "-----END PUBLIC KEY-----",
                            ""
                    )
                    .replaceAll("\\s", "");

            byte[] decodedKey =
                    Base64.getDecoder().decode(encodedKey);

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(decodedKey);

            KeyFactory keyFactory =
                    KeyFactory.getInstance("RSA");

            return (RSAPublicKey)
                    keyFactory.generatePublic(keySpec);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot load JWT verification key from "
                            + location,
                    exception
            );
        }
    }
}