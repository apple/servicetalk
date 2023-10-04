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

/**
 * A subclass of {@link ProxyConnectException} that indicates an unexpected response status from a proxy received for
 * <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6">HTTP/1.1 CONNECT</a> request.
 *
 * @see SingleAddressHttpClientBuilder#proxyAddress(Object)
 */
public class ProxyConnectResponseException extends ProxyConnectException {

    private static final long serialVersionUID = 5117046591069113007L;

    private final HttpResponseMetaData response;

    /**
     * Creates a new instance.
     *
     * @param message the detail message
     * @param response {@link HttpResponseMetaData} of the received response
     */
    public ProxyConnectResponseException(final String message, final HttpResponseMetaData response) {
        super(message);
        this.response = response;
    }

    /**
     * Returns {@link HttpResponseMetaData} that was received in response to
     * <a href="https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6">HTTP/1.1 CONNECT</a> request.
     *
     * @return {@link HttpResponseMetaData} that was received
     */
    public HttpResponseMetaData response() {
        return response;
    }
}
