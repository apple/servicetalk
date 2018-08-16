/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.utils.auth;

import io.servicetalk.http.api.HttpResponseStatuses;

import static java.util.Objects.requireNonNull;

/**
 * An authentication exception, which indicates that access was denied and usually converts to
 * {@link HttpResponseStatuses#UNAUTHORIZED 401 (Unauthorized)} or
 * {@link HttpResponseStatuses#PROXY_AUTHENTICATION_REQUIRED 407 (Proxy Authentication Required)} response.
 */
public class AuthenticationException extends RuntimeException {

    private static final long serialVersionUID = -5269525787330777566L;

    /**
     * Creates a new instance.
     *
     * @param message a cause for denied access
     */
    public AuthenticationException(final String message) {
        super(requireNonNull(message));
    }
}
