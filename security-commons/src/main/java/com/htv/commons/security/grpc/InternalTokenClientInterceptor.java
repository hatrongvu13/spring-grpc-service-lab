package com.htv.commons.security.grpc;

import com.htv.commons.security.token.InternalTokenIssuer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import java.util.Collection;
import java.util.Objects;

public final class InternalTokenClientInterceptor
        implements ClientInterceptor {

    private static final Metadata.Key<String>
            AUTHORIZATION_METADATA_KEY =
            Metadata.Key.of(
                    "authorization",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private final InternalTokenIssuer tokenIssuer;

    private final String audience;

    private final Collection<String> scopes;

    public InternalTokenClientInterceptor(
            InternalTokenIssuer tokenIssuer,
            String audience,
            Collection<String> scopes) {

        this.tokenIssuer = Objects.requireNonNull(
                tokenIssuer,
                "tokenIssuer must not be null"
        );

        this.audience = Objects.requireNonNull(
                audience,
                "audience must not be null"
        );

        this.scopes = scopes == null
                ? java.util.List.of()
                : java.util.List.copyOf(scopes);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall
                .SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)
        ) {

            @Override
            public void start(
                    Listener<RespT> responseListener,
                    Metadata headers) {

                String token = tokenIssuer.issue(
                        audience,
                        scopes
                );

                headers.put(
                        AUTHORIZATION_METADATA_KEY,
                        "Bearer " + token
                );

                super.start(responseListener, headers);
            }
        };
    }
}