package com.htv.commons.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh: map key chứa dấu chấm (full gRPC method name) PHẢI dùng
 * cú pháp ngoặc vuông [...] trong application.yml, nếu không Spring
 * relaxed-binding tách key theo '.' và rule không bao giờ khớp — gây
 * PERMISSION_DENIED "No authorization rule is configured".
 */
class ServiceLabSecurityPropertiesBindingTest {

    private ServiceLabSecurityProperties bind(Map<String, String> props) {
        ConfigurationPropertySource source =
                new MapConfigurationPropertySource(props);
        return new Binder(source)
                .bind("service-lab.security", ServiceLabSecurityProperties.class)
                .orElseGet(ServiceLabSecurityProperties::new);
    }

    @Test
    void bracketedDottedKeyIsPreservedWhole() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(
                "service-lab.security.authorization[servicelab.ping.v1.PingService/Ping][0]",
                "ping:execute");

        ServiceLabSecurityProperties bound = bind(props);

        assertThat(bound.getAuthorization())
                .containsKey("servicelab.ping.v1.PingService/Ping");
        assertThat(bound.getAuthorization()
                .get("servicelab.ping.v1.PingService/Ping"))
                .containsExactly("ping:execute");
    }
}
