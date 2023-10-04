/*
 * Copyright © 2019, 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ProxyConnectResponseException;
import io.servicetalk.transport.api.RetryableException;

/**
 * A proxy response exception, that indicates an unexpected response status from a proxy.
 *
 * @deprecated Use {@link ProxyConnectResponseException} instead
 */
@Deprecated // FIXME: 0.43 - remove deprecated class
public class ProxyResponseException extends ProxyConnectResponseException implements RetryableException {
    private static final long serialVersionUID = -1021287419155443499L;

    ProxyResponseException(final String message, final HttpResponseMetaData response) {
        super(message, response);
    }

    /**
     * Returns the {@link HttpResponseStatus} that was received.
     *
     * @return the {@link HttpResponseStatus} that was received.
     */
    public HttpResponseStatus status() {
        return response().status();
    }
}
