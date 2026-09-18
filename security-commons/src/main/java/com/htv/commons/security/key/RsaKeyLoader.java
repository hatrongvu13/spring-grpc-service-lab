package com.htv.commons.security.key;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyLoader {

    private final ResourceLoader resourceLoader;

    public RsaKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public RSAPublicKey loadPublicKey(String location) {
        String pem = readPem(location);

        String encoded = pem
                .replace(
                        "-----BEGIN PUBLIC KEY-----",
                        ""
                )
                .replace(
                        "-----END PUBLIC KEY-----",
                        ""
                )
                .replaceAll("\\s+", "");

        try {
            byte[] bytes =
                    Base64.getDecoder().decode(encoded);

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(bytes);

            return (RSAPublicKey) KeyFactory
                    .getInstance("RSA")
                    .generatePublic(keySpec);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot load RSA public key from "
                            + location,
                    exception
            );
        }
    }

    public RSAPrivateKey loadPrivateKey(String location) {
        String pem = readPem(location);

        String encoded = pem
                .replace(
                        "-----BEGIN PRIVATE KEY-----",
                        ""
                )
                .replace(
                        "-----END PRIVATE KEY-----",
                        ""
                )
                .replaceAll("\\s+", "");

        try {
            byte[] bytes =
                    Base64.getDecoder().decode(encoded);

            PKCS8EncodedKeySpec keySpec =
                    new PKCS8EncodedKeySpec(bytes);

            return (RSAPrivateKey) KeyFactory
                    .getInstance("RSA")
                    .generatePrivate(keySpec);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot load RSA private key from "
                            + location,
                    exception
            );
        }
    }

    private String readPem(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException(
                    "Key location must not be blank"
            );
        }

        Resource resource =
                resourceLoader.getResource(location);

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Key was not found at " + location
            );
        }

        if (!resource.isReadable()) {
            throw new IllegalStateException(
                    "Key is not readable at " + location
            );
        }

        try (InputStream inputStream =
                     resource.getInputStream()) {

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.US_ASCII
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot read key from " + location,
                    exception
            );
        }
    }
}