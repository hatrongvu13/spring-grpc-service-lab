package com.htv.commons.security.grpc;

import com.htv.commons.security.authorization.GrpcAuthorizationPolicy;
import com.htv.commons.security.authorization.GrpcAuthorizationPolicy.AuthorizationDecision;
import com.htv.commons.security.token.InternalTokenVerifier;
import com.htv.commons.security.token.InternalTokenVerifier.InternalPrincipal;
import com.htv.commons.security.token.InternalTokenVerifier.InternalTokenVerificationException;
import io.grpc.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Objects;

/**
 * Global gRPC interceptor thực hiện:
 *
 * <ul>
 *     <li>Đọc internal bearer token từ gRPC Metadata.</li>
 *     <li>Xác minh JWT.</li>
 *     <li>Kiểm tra quyền theo full gRPC method name.</li>
 *     <li>Đưa principal và token vào gRPC Context.</li>
 *     <li>Tùy chọn đưa Authentication vào Spring SecurityContext.</li>
 * </ul>
 */
public final class AuthorizationInterceptor implements ServerInterceptor {

    /**
     * Principal đã xác thực của request hiện tại.
     */
    public static final Context.Key<InternalPrincipal>
            INTERNAL_PRINCIPAL_CONTEXT =
            Context.key("service-lab-internal-principal");

    /**
     * Internal token của request hiện tại.
     * <p>
     * Không nên ghi token này vào log.
     */
    public static final Context.Key<String>
            INTERNAL_TOKEN_CONTEXT =
            Context.key("service-lab-internal-token");

    private final InternalTokenVerifier tokenVerifier;

    private final GrpcAuthorizationPolicy authorizationPolicy;

    private final Metadata.Key<String> authorizationMetadataKey;

    private final String bearerPrefix;

    private final int maximumTokenLength;

    private final boolean propagateSecurityContext;

    public AuthorizationInterceptor(
            InternalTokenVerifier tokenVerifier,
            GrpcAuthorizationPolicy authorizationPolicy,
            String authorizationHeader,
            String bearerPrefix,
            int maximumTokenLength,
            boolean propagateSecurityContext) {

        this.tokenVerifier = Objects.requireNonNull(
                tokenVerifier,
                "tokenVerifier must not be null"
        );

        this.authorizationPolicy = Objects.requireNonNull(
                authorizationPolicy,
                "authorizationPolicy must not be null"
        );

        String normalizedHeader = requireText(
                authorizationHeader,
                "authorizationHeader"
        ).toLowerCase(Locale.ROOT);

        this.authorizationMetadataKey = Metadata.Key.of(
                normalizedHeader,
                Metadata.ASCII_STRING_MARSHALLER
        );

        this.bearerPrefix = bearerPrefix == null
                ? "Bearer "
                : bearerPrefix;

        if (maximumTokenLength <= 0) {
            throw new IllegalArgumentException(
                    "maximumTokenLength must be greater than zero"
            );
        }

        this.maximumTokenLength = maximumTokenLength;
        this.propagateSecurityContext = propagateSecurityContext;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        Objects.requireNonNull(call, "call must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(next, "next must not be null");

        MethodDescriptor<ReqT, RespT> methodDescriptor =
                call.getMethodDescriptor();

        String fullMethodName =
                methodDescriptor.getFullMethodName();

        String serviceName =
                methodDescriptor.getServiceName();

        /*
         * Public service hoặc public RPC không yêu cầu token.
         */
        if (authorizationPolicy.isPublic(
                fullMethodName,
                serviceName)) {

            return next.startCall(call, headers);
        }

        String authorization =
                headers.get(authorizationMetadataKey);

        if (authorization == null || authorization.isBlank()) {
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Missing authorization metadata"
                    )
            );
        }

        if (!authorization.startsWith(bearerPrefix)) {
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Authorization metadata must use "
                                    + "the configured bearer prefix"
                    )
            );
        }

        String token = authorization
                .substring(bearerPrefix.length())
                .trim();

        if (token.isEmpty()) {
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Bearer token is empty"
                    )
            );
        }

        if (token.length() > maximumTokenLength) {
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Bearer token exceeds maximum length"
                    )
            );
        }

        InternalPrincipal principal;

        try {
            principal = tokenVerifier.verify(token);
        } catch (InternalTokenVerificationException exception) {
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            safeDescription(
                                    exception.getMessage(),
                                    "Internal token is invalid"
                            )
                    )
            );
        } catch (RuntimeException exception) {
            /*
             * Không đưa chi tiết lỗi decoder, public key hoặc
             * crypto ra bên ngoài.
             */
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Internal token verification failed"
                    )
            );
        }

        AuthorizationDecision decision =
                authorizationPolicy.authorize(
                        fullMethodName,
                        serviceName,
                        principal
                );

        if (!decision.granted()) {
            return rejectUnauthorizedCall(
                    call,
                    decision
            );
        }

        Context grpcContext = Context.current()
                .withValue(
                        INTERNAL_PRINCIPAL_CONTEXT,
                        principal
                )
                .withValue(
                        INTERNAL_TOKEN_CONTEXT,
                        token
                );

        if (!propagateSecurityContext) {
            return Contexts.interceptCall(
                    grpcContext,
                    call,
                    headers,
                    next
            );
        }

        return interceptWithSpringSecurityContext(
                grpcContext,
                principal,
                call,
                headers,
                next
        );
    }

    /**
     * Chuyển quyết định phân quyền thành gRPC status tương ứng.
     */
    private static <ReqT, RespT>
    ServerCall.Listener<ReqT> rejectUnauthorizedCall(
            ServerCall<ReqT, RespT> call,
            AuthorizationDecision decision) {

        if (decision.authenticationRequired()) {
            return closeCall(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            safeDescription(
                                    decision.reason(),
                                    "Authentication is required"
                            )
                    )
            );
        }

        Status status = Status.PERMISSION_DENIED.withDescription(
                safeDescription(
                        decision.reason(),
                        "Permission denied"
                )
        );

        if (!decision.missingScopes().isEmpty()) {
            status = status.augmentDescription(
                    "Missing scopes: "
                            + String.join(
                            ", ",
                            decision.missingScopes()
                    )
            );
        }

        return closeCall(call, status);
    }

    /**
     * Tạo Authentication cho Spring Security trong lúc khởi tạo
     * listener của gRPC call.
     * <p>
     * Principal chính của gRPC request vẫn được lưu trong gRPC Context.
     */
    private <ReqT, RespT> ServerCall.Listener<ReqT>
    interceptWithSpringSecurityContext(
            Context grpcContext,
            InternalPrincipal principal,
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        SecurityContext previousSecurityContext =
                SecurityContextHolder.getContext();

        SecurityContext requestSecurityContext =
                SecurityContextHolder.createEmptyContext();

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.authorities()
                );

        requestSecurityContext.setAuthentication(authentication);

        try {
            SecurityContextHolder.setContext(
                    requestSecurityContext
            );

            ServerCall.Listener<ReqT> delegate =
                    Contexts.interceptCall(
                            grpcContext,
                            call,
                            headers,
                            next
                    );

            return new SecurityContextServerCallListener<>(
                    delegate,
                    requestSecurityContext
            );
        } finally {
            restoreSecurityContext(previousSecurityContext);
        }
    }

    /**
     * Đóng gRPC call và trả listener không xử lý thêm message.
     */
    private static <ReqT, RespT>
    ServerCall.Listener<ReqT> closeCall(
            ServerCall<ReqT, RespT> call,
            Status status) {

        call.close(status, new Metadata());

        return new ServerCall.Listener<>() {
            // Request đã bị từ chối, không xử lý callback.
        };
    }

    /**
     * Lấy principal từ gRPC Context tại callback hiện tại.
     */
    public static InternalPrincipal currentPrincipal() {
        return INTERNAL_PRINCIPAL_CONTEXT.get();
    }

    /**
     * Lấy internal token từ gRPC Context tại callback hiện tại.
     * <p>
     * Không nên ghi giá trị trả về vào log.
     */
    public static String currentToken() {
        return INTERNAL_TOKEN_CONTEXT.get();
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

    private static String safeDescription(
            String description,
            String defaultDescription) {

        if (description == null || description.isBlank()) {
            return defaultDescription;
        }

        return description;
    }

    private static void restoreSecurityContext(
            SecurityContext previousSecurityContext) {

        if (previousSecurityContext == null) {
            SecurityContextHolder.clearContext();
            return;
        }

        SecurityContextHolder.setContext(previousSecurityContext);
    }

    /**
     * gRPC có thể gọi listener callback sau khi interceptCall đã kết thúc.
     * Listener wrapper này thiết lập lại Spring SecurityContext cho từng
     * callback và phục hồi context cũ sau khi callback hoàn thành.
     */
    private static final class SecurityContextServerCallListener<ReqT>
            extends ServerCall.Listener<ReqT> {

        private final ServerCall.Listener<ReqT> delegate;

        private final SecurityContext securityContext;

        private SecurityContextServerCallListener(
                ServerCall.Listener<ReqT> delegate,
                SecurityContext securityContext) {

            this.delegate = Objects.requireNonNull(
                    delegate,
                    "delegate must not be null"
            );

            this.securityContext = Objects.requireNonNull(
                    securityContext,
                    "securityContext must not be null"
            );
        }

        @Override
        public void onMessage(ReqT message) {
            runWithSecurityContext(
                    () -> delegate.onMessage(message)
            );
        }

        @Override
        public void onHalfClose() {
            runWithSecurityContext(delegate::onHalfClose);
        }

        @Override
        public void onCancel() {
            runWithSecurityContext(delegate::onCancel);
        }

        @Override
        public void onComplete() {
            runWithSecurityContext(delegate::onComplete);
        }

        @Override
        public void onReady() {
            runWithSecurityContext(delegate::onReady);
        }

        private void runWithSecurityContext(Runnable callback) {
            SecurityContext previousSecurityContext =
                    SecurityContextHolder.getContext();

            try {
                SecurityContextHolder.setContext(
                        securityContext
                );

                callback.run();
            } finally {
                restoreSecurityContext(previousSecurityContext);
            }
        }
    }
}