package com.htv.commons.security.token;

import java.util.Collection;

public interface InternalTokenIssuer {

    /**
     * Phát token cho một downstream audience.
     *
     * @param audience audience của service nhận token
     * @param scopes   các scope được cấp
     * @return compact JWT
     */
    String issue(
            String audience,
            Collection<String> scopes
    );
}
