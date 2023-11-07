/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import java.io.IOException;

/**
 * An exception while processing
 * <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6">HTTP/1.1 CONNECT</a> request.
 *
 * @see SingleAddressHttpClientBuilder#proxyConfig(ProxyConfig)
 */
public class ProxyConnectException extends IOException {

    private static final long serialVersionUID = 4453075928788773272L;

    /**
     * Creates a new instance.
     *
     * @param message the detail message
     */
    public ProxyConnectException(final String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     *
     * @param message the detail message
     * @param cause the original cause
     */
    public ProxyConnectException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
