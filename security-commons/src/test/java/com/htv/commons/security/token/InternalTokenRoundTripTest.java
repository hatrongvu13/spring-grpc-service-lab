package com.htv.commons.security.token;

import com.htv.commons.security.key.RsaKeyLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tái hiện chuỗi phát/xác minh internal token đúng như runtime:
 * edge-gateway ký RS256 rồi dispatch-service xác minh bằng cùng public key.
 *
 * Mục tiêu: cô lập lỗi UNAUTHENTICATED "Internal token verification failed"
 * xảy ra khi gọi /api/v1/ping.
 */
class InternalTokenRoundTripTest {

    private static final String ISSUER = "edge-gateway";
    private static final String SERVICE_ID = "edge-gateway";
    private static final String AUDIENCE = "dispatch-service";
    private static final String KEY_ID = "gateway-internal-token-key";

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    static void generateInMemoryKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();
    }

    private JwtEncoder encoder() {
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                .algorithm(SignatureAlgorithm.RS256)
                .jwkPostProcessor(jwk -> jwk.keyID(KEY_ID))
                .build();
    }

    private JwtDecoder decoder() {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private JwtInternalTokenIssuer issuer(JwtEncoder encoder) {
        return new JwtInternalTokenIssuer(
                encoder, ISSUER, SERVICE_ID,
                Duration.ofMinutes(2), Duration.ofSeconds(5),
                SignatureAlgorithm.RS256, KEY_ID);
    }

    private JwtInternalTokenVerifier verifier(JwtDecoder decoder) {
        return new JwtInternalTokenVerifier(
                decoder, AUDIENCE, List.of(ISSUER), "scope", "service_id");
    }

    @Test
    void tokenIssuedByGatewayVerifiesAtDispatch() {
        String token = issuer(encoder()).issue(AUDIENCE, List.of("ping:execute"));

        assertThatNoException().isThrownBy(() -> {
            InternalTokenVerifier.InternalPrincipal principal =
                    verifier(decoder()).verify(token);
            assertThat(principal.serviceId()).isEqualTo(SERVICE_ID);
            assertThat(principal.hasScope("ping:execute")).isTrue();
        });
    }
}
