package com.htv.commons.security.token;

import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;
import java.util.Collection;
import java.util.Set;

/**
 * Xác minh internal token và chuyển token thành principal dùng trong hệ thống.
 *
 * Interface này không phụ thuộc trực tiếp vào cách token được phát hành.
 * Có thể thay JwtInternalTokenVerifier bằng implementation khác như mTLS,
 * opaque token hoặc signed service credential.
 */
@FunctionalInterface
public interface InternalTokenVerifier {

    /**
     * Xác minh token và trả về principal đã được tin cậy.
     *
     * @param token token không chứa tiền tố "Bearer "
     * @return principal đã xác minh
     * @throws InternalTokenVerificationException nếu token không hợp lệ
     */
    InternalPrincipal verify(String token);

    /**
     * Principal nội bộ được lưu trong gRPC Context.
     *
     * @param subject     giá trị claim sub
     * @param issuer      giá trị claim iss
     * @param serviceId   service gọi request, thường từ claim service_id
     * @param audience    danh sách audience của token
     * @param scopes      scope đã chuẩn hóa
     * @param authorities Spring Security authorities dạng SCOPE_xxx
     */
    record InternalPrincipal(
            String subject,
            String issuer,
            String serviceId,
            Set<String> audience,
            Set<String> scopes,
            Collection<? extends GrantedAuthority> authorities
    ) implements Principal {

        public InternalPrincipal {
            audience = audience == null
                    ? Set.of()
                    : Set.copyOf(audience);

            scopes = scopes == null
                    ? Set.of()
                    : Set.copyOf(scopes);

            authorities = authorities == null
                    ? ListBackedAuthorities.empty()
                    : ListBackedAuthorities.copyOf(authorities);
        }

        @Override
        public String getName() {
            if (subject != null && !subject.isBlank()) {
                return subject;
            }

            if (serviceId != null && !serviceId.isBlank()) {
                return serviceId;
            }

            return "unknown-internal-service";
        }

        public boolean hasScope(String scope) {
            return scope != null && scopes.contains(scope);
        }
    }

    /**
     * Exception thống nhất để interceptor chuyển thành UNAUTHENTICATED.
     */
    class InternalTokenVerificationException extends RuntimeException {

        public InternalTokenVerificationException(String message) {
            super(message);
        }

        public InternalTokenVerificationException(
                String message,
                Throwable cause) {

            super(message, cause);
        }
    }

    /**
     * Helper nội bộ để tạo immutable authority collection.
     */
    final class ListBackedAuthorities {

        private ListBackedAuthorities() {
        }

        static Collection<? extends GrantedAuthority> empty() {
            return Set.of();
        }

        static Collection<? extends GrantedAuthority> copyOf(
                Collection<? extends GrantedAuthority> authorities) {

            return Set.copyOf(authorities);
        }
    }
}